package com.tiny.platform.application.oauth.workflow.smoke;

import com.tiny.platform.OauthServerApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * 用于脚本化执行 Camunda 最小流程 smoke。
 */
public final class CamundaSmokeVerifierApplication {

    private static final Logger log = LoggerFactory.getLogger(CamundaSmokeVerifierApplication.class);

    private CamundaSmokeVerifierApplication() {
    }

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(OauthServerApplication.class);
        String[] effectiveArgs = withRandomPortWhenMissing(args);

        int exitCode = 1;
        try (ConfigurableApplicationContext context = application.run(effectiveArgs)) {
            context.getBean(CamundaSmokeVerifier.class).verify();
            exitCode = 0;
        } catch (Exception ex) {
            log.error("Camunda SB4 smoke failed", ex);
        }

        System.exit(exitCode);
    }

    private static String[] withRandomPortWhenMissing(String[] args) {
        for (String arg : args) {
            if (arg != null && arg.startsWith("--server.port=")) {
                return args;
            }
        }

        String[] effectiveArgs = new String[args.length + 1];
        effectiveArgs[0] = "--server.port=0";
        System.arraycopy(args, 0, effectiveArgs, 1, args.length);
        return effectiveArgs;
    }
}
