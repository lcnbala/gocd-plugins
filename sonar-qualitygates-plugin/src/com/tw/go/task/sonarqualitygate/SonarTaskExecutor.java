
package com.tw.go.task.sonarqualitygate;


import com.thoughtworks.go.plugin.api.task.*;
import com.tw.go.plugin.common.*;
import com.tw.go.plugin.common.TaskExecutor;
import org.json.JSONObject;

import java.security.GeneralSecurityException;
import java.util.Map;

public class SonarTaskExecutor extends TaskExecutor {

    public SonarTaskExecutor(JobConsoleLogger console, Context context, Map config) {
        super(console, context, config);
    }

    public Result execute() {

        String sonarProjectKey = (String) ((Map) this.config.get(SonarScanTask.SONAR_PROJECT_KEY)).get(GoApiConstants.PROPERTY_NAME_VALUE);
        log("checking quality gate result for: " + sonarProjectKey);

        try {
            // get input parameter
            String stageName = (String) ((Map)this.config.get(SonarScanTask.STAGE_NAME)).get(GoApiConstants.PROPERTY_NAME_VALUE);
            String jobName = (String) ((Map)this.config.get(SonarScanTask.JOB_NAME)).get(GoApiConstants.PROPERTY_NAME_VALUE);
            String jobCounter = (String) ((Map)this.config.get(SonarScanTask.JOB_COUNTER)).get(GoApiConstants.PROPERTY_NAME_VALUE);

            String sonarApiUrl = (String) ((Map)this.config.get(SonarScanTask.SONAR_API_URL)).get(GoApiConstants.PROPERTY_NAME_VALUE);
            log("API Url: " + sonarApiUrl);
            String issueTypeFail = (String) ((Map) this.config.get(SonarScanTask.ISSUE_TYPE_FAIL)).get(GoApiConstants.PROPERTY_NAME_VALUE);
            log("Fail if: " + issueTypeFail);

            SonarClient sonarClient = new SonarClient(sonarApiUrl);

            // get quality gate details
            JSONObject result = sonarClient.getProjectWithQualityGateDetails(sonarProjectKey);

            if (!(stageName == null) && !(jobName == null) && !(jobCounter == null)) {

                String scheduledTime = getScheduledTime();
                String resultDate = result.getString("date");
                resultDate = new StringBuilder(resultDate).insert(resultDate.length()-2, ":").toString();

                int timeout = 0;
                int timeoutTime = 60000;
                int timeLimit = 300000;

                while(compareDates(resultDate, scheduledTime) <= 0) {

                    log("Nessus scan result is older than the start of the pipeline. Waiting for a newer scan ...");

                    result = sonarClient.getProjectWithQualityGateDetails(sonarProjectKey);
                    timeout = timeout + timeoutTime;

                    resultDate = result.getString("date");
                    resultDate = new StringBuilder(resultDate).insert(resultDate.length()-2, ":").toString();

                    if (timeout > timeLimit) {

                        log("No new scan has been found !");

                        return new Result(false, "Failed to get a newer quality gate for " + sonarProjectKey
                                + ". The present quality gate is older than the start of the Sonar scan task.");
                    }

                    Thread.sleep(timeoutTime);

                }

                SonarParser parser = new SonarParser(result);

                // check that a quality gate is returned
                JSONObject qgDetails = parser.GetQualityGateDetails();

                String qgResult = qgDetails.getString("level");

                // get result issues
                return parseResult(qgResult, issueTypeFail);

            }
            else {

                SonarParser parser = new SonarParser(result);

                // check that a quality gate is returned
                JSONObject qgDetails = parser.GetQualityGateDetails();

                String qgResult = qgDetails.getString("level");

                // get result issues
                return parseResult(qgResult, issueTypeFail);
            }

        } catch (Exception e) {
            log("Error during get or parse of quality gate result. Please check if a quality gate is defined" + e.getMessage());
            return new Result(false, "Failed to get quality gate for " + sonarProjectKey + ". Please check if a quality gate is defined", e);
        }
    }

    private Result parseResult(String qgResult, String issueTypeFail) {

        switch (issueTypeFail) {
            case "error" :
                if("ERROR".equals(qgResult))
                {
                    return new Result(false, "At least one Error in Quality Gate");
                }
                break;
            case "warning" :
                if("ERROR".equals(qgResult) || "WARN".equals(qgResult))
                {
                    return new Result(false, "At least one Error or Warning in Quality Gate");
                }
                break;
        }
        return new Result(true, "SonarQube quality gate passed");
    }

    protected String getScheduledTime() throws GeneralSecurityException {
        Map envVars = context.getEnvironmentVariables();
        GoApiClient client = new GoApiClient(envVars.get(GoApiConstants.ENVVAR_NAME_GO_SERVER_URL).toString());

        try {
            // get go build user authorization
            if (envVars.get(GoApiConstants.ENVVAR_NAME_GO_BUILD_USER) != null &&
                    envVars.get(GoApiConstants.ENVVAR_NAME_GO_BUILD_USER_PASSWORD) != null) {

                client.setBasicAuthentication(envVars.get(GoApiConstants.ENVVAR_NAME_GO_BUILD_USER).toString(), envVars.get(GoApiConstants.ENVVAR_NAME_GO_BUILD_USER_PASSWORD).toString());

                log("Authorization set");
            }

            String scheduledTime = client.getJobProperty(
                    envVars.get("GO_PIPELINE_NAME").toString(),
                    envVars.get("GO_PIPELINE_COUNTER").toString(),
                    (String) ((Map)this.config.get(SonarScanTask.STAGE_NAME)).get(GoApiConstants.PROPERTY_NAME_VALUE),
                    (String) ((Map)this.config.get(SonarScanTask.JOB_COUNTER)).get(GoApiConstants.PROPERTY_NAME_VALUE),
                    (String) ((Map)this.config.get(SonarScanTask.JOB_NAME)).get(GoApiConstants.PROPERTY_NAME_VALUE),
                    "cruise_timestamp_01_scheduled");

            return scheduledTime;

        }
        catch(Exception e)
        {
            log(e.toString());
            return "0";
        }
    }

    protected int compareDates(String date1, String date2)
    {
        return (date1.compareTo(date2));
    }

    protected String getPluginLogPrefix(){
        return "[SonarQube Quality Gate Plugin] ";
    }

 }