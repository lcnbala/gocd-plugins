package com.tw.go.task.dockerpipeline;

import com.google.gson.GsonBuilder;
import com.thoughtworks.go.plugin.api.GoApplicationAccessor;
import com.thoughtworks.go.plugin.api.GoPlugin;
import com.thoughtworks.go.plugin.api.GoPluginIdentifier;
import com.thoughtworks.go.plugin.api.annotation.Extension;
import com.thoughtworks.go.plugin.api.exceptions.UnhandledRequestTypeException;
import com.thoughtworks.go.plugin.api.logging.Logger;
import com.thoughtworks.go.plugin.api.request.GoPluginApiRequest;
import com.thoughtworks.go.plugin.api.response.DefaultGoApiResponse;
import com.thoughtworks.go.plugin.api.response.DefaultGoPluginApiResponse;
import com.thoughtworks.go.plugin.api.response.GoPluginApiResponse;
import com.thoughtworks.go.plugin.api.task.JobConsoleLogger;
import com.tw.go.plugin.common.Context;
import com.tw.go.plugin.common.Result;
import org.apache.commons.io.IOUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by BradeaC on 14/12/2015.
 */
@Extension
public class DockerTask implements GoPlugin
{
    public static final String IS_DOCKER_CLEAN = "IsDockerClean";

    public static final String IMAGE_NAME = "ImageName";
    public static final String DOCKER_FILE_NAME = "DockerFileName";
    public static final String BUILD_ARGS = "BuildArgs";

    public static final String USERNAME = "Username";
    public static final String IMAGE_TAG = "ImageTag";

    public static final String REGISTRY_USERNAME = "RegistryUsername";
    public static final String REGISTRY_PASSWORD = getPasswordName();

    public static final String REGISTRY_EMAIL = "RegistryEmail";
    public static final String REGISTRY_URL_FOR_LOGIN = "RegistryURLForLogin";

    public static final String IS_DOCKER_CLEAN_AFTER = "IsDockerCleanAfter";

    private static final Logger LOGGER = Logger.getLoggerFor(DockerTask.class);

    private static final String DEFAULTVALUE = "default-value";
    private static final String REQUIRED = "required";

    private static String getPasswordName()
    {
        return "RegistryPassword";
    }

    @Override
    public void initializeGoApplicationAccessor(GoApplicationAccessor goApplicationAccessor)
    {
        //shouldn't be
        //implemented
    }

    @Override
    public GoPluginApiResponse handle(GoPluginApiRequest request) throws UnhandledRequestTypeException
    {
        if ("configuration".equals(request.requestName()))
        {
            return handleGetConfigRequest();
        }
        else if ("validate".equals(request.requestName()))
        {
            return handleValidation();
        }
        else if ("execute".equals(request.requestName()))
        {
            return handleTaskExecution(request);
        }
        else if ("view".equals(request.requestName()))
        {
            return handleTaskView();
        }
        throw new UnhandledRequestTypeException(request.requestName());
    }

    private GoPluginApiResponse handleTaskView()
    {
        int responseCode = DefaultGoApiResponse.SUCCESS_RESPONSE_CODE;

        HashMap view = new HashMap();
        view.put("displayValue", "Docker pipeline plugin");

        try
        {
            view.put("template", IOUtils.toString(getClass().getResourceAsStream("/views/task.template.html"), "UTF-8"));
        }
        catch (Exception e)
        {
            responseCode = DefaultGoApiResponse.INTERNAL_ERROR;
            String errorMessage = "Failed to find template: " + e.getMessage();
            view.put("exception", errorMessage);

            LOGGER.error(errorMessage, e);
        }
        return createResponse(responseCode, view);
    }

    private GoPluginApiResponse handleTaskExecution(GoPluginApiRequest request)
    {
        Map executionRequest = (Map) new GsonBuilder().create().fromJson(request.requestBody(), Object.class);
        Map config = (Map) executionRequest.get("config");
        Map context = (Map) executionRequest.get("context");

        DockerTaskExecutor executor = new DockerTaskExecutor(JobConsoleLogger.getConsoleLogger(), new Context(context), config);

        Result result = executor.execute(new Config(config), new Context(context));

        return createResponse(result.responseCode(), result.toMap());
    }

    private GoPluginApiResponse handleValidation()
    {
        HashMap validationResult = new HashMap();
        int responseCode = DefaultGoPluginApiResponse.SUCCESS_RESPONSE_CODE;

        return createResponse(responseCode, validationResult);
    }

    private GoPluginApiResponse createResponse(int responseCode, Map body)
    {
        final DefaultGoPluginApiResponse response = new DefaultGoPluginApiResponse(responseCode);
        response.setResponseBody(new GsonBuilder().serializeNulls().create().toJson(body));

        return response;
    }

    private GoPluginApiResponse handleGetConfigRequest()
    {
        HashMap config = new HashMap();

        addDockerCleanConfig(config);
        addDockerBuildConfig(config);
        addDockerTagConfig(config);
        addDockerLoginConfig(config);
        addDockerCleanAfter(config);

        return createResponse(DefaultGoPluginApiResponse.SUCCESS_RESPONSE_CODE, config);
    }

    private static void addDockerCleanConfig(HashMap config)
    {
        HashMap isDockerClean = new HashMap();
        isDockerClean.put(DEFAULTVALUE, "true");
        isDockerClean.put(REQUIRED, true);
        config.put(IS_DOCKER_CLEAN, isDockerClean);
    }

    private static void addDockerBuildConfig(HashMap config)
    {
        HashMap imageName = new HashMap();
        imageName.put(DEFAULTVALUE, "");
        imageName.put(REQUIRED, true);

        config.put(IMAGE_NAME, imageName);


        HashMap dockerFileName = new HashMap();
        dockerFileName.put(DEFAULTVALUE, "");
        dockerFileName.put(REQUIRED, false);

        config.put(DOCKER_FILE_NAME, dockerFileName);

        HashMap buildArgs = new HashMap();
        buildArgs.put(DEFAULTVALUE, "");
        buildArgs.put(REQUIRED, false);

        config.put(BUILD_ARGS, buildArgs);
    }

    private static void addDockerTagConfig(HashMap config)
    {
        HashMap username = new HashMap();
        username.put(DEFAULTVALUE, "");
        username.put(REQUIRED, false);

        config.put(USERNAME, username);


        HashMap imageTag = new HashMap();
        imageTag.put(DEFAULTVALUE, "");
        imageTag.put(REQUIRED, true);

        config.put(IMAGE_TAG, imageTag);
    }

    private static void addDockerLoginConfig(HashMap config)
    {
        HashMap registryUsername = new HashMap();
        registryUsername.put(DEFAULTVALUE, "");
        registryUsername.put(REQUIRED, false);

        config.put(REGISTRY_USERNAME, registryUsername);


        HashMap registryPassword = new HashMap();
        registryPassword.put(DEFAULTVALUE, "");
        registryPassword.put(REQUIRED, false);

        config.put(REGISTRY_PASSWORD, registryPassword);


        HashMap registryEmail = new HashMap();
        registryEmail.put(DEFAULTVALUE, "");
        registryEmail.put(REQUIRED, false);

        config.put(REGISTRY_EMAIL, registryEmail);


        HashMap registryURLForLogin = new HashMap();
        registryURLForLogin.put(DEFAULTVALUE, "");
        registryURLForLogin.put(REQUIRED, true);

        config.put(REGISTRY_URL_FOR_LOGIN, registryURLForLogin);
    }

    private static void addDockerCleanAfter(HashMap config)
    {
        HashMap isDockerCleanAfter = new HashMap();
        isDockerCleanAfter.put(DEFAULTVALUE, "true");
        isDockerCleanAfter.put(REQUIRED, true);
        config.put(IS_DOCKER_CLEAN_AFTER, isDockerCleanAfter);
    }

    @Override
    public GoPluginIdentifier pluginIdentifier()
    {
        return new GoPluginIdentifier("task", Arrays.asList("1.0"));
    }
}
