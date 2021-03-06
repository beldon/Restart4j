package com.restart4j;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import oshi.PlatformEnum;
import oshi.SystemInfo;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;

import java.io.IOException;
import java.util.Optional;
import java.util.function.Function;

/**
 * For restarting the application.
 * An {@link ApplicationRestart} can be created through builder api.
 *
 * @author Daniel Gyorffy
 */
public final class ApplicationRestart {

    private static final Logger logger = LoggerFactory.getLogger(ApplicationRestart.class);

    private static final Runnable EMPTY_RUNNABLE = () -> {};
    private static final Function<String, String> EMPTY_FUNCTION = it -> it;

    private static final char NUL_CHAR = '\00';
    private static final char SPACE = '\u0020';

    private final Runnable beforeNewProcessCreated;
    private final Runnable beforeCurrentProcessTerminated;
    private final Runnable terminationPolicy;

    private final Function<String, String> cmdModifier;

    private ApplicationRestart() {
        this(null, null, null, null);
    }

    private ApplicationRestart(Runnable beforeNewProcessCreated,
                               Runnable beforeCurrentProcessTerminated,
                               Runnable terminationPolicy,
                               Function<String, String> cmdModifier) {
        this.beforeNewProcessCreated = beforeNewProcessCreated;
        this.beforeCurrentProcessTerminated = beforeCurrentProcessTerminated;
        this.terminationPolicy = terminationPolicy;
        this.cmdModifier = cmdModifier;
    }

    private ApplicationRestart(Builder builder) {
        this(
                builder.beforeNewProcessCreated,
                builder.beforeCurrentProcessTerminated,
                builder.terminationPolicy,
                builder.cmdModifier
        );
    }

    public void restartApp() throws RestartException {
        try {
            Optional<OSProcess> appProcess = getAppProcess();
            OSProcess raw = appProcess.orElseThrow(() -> new RestartException("Couldn't identify the process by PID"));
            Optional.ofNullable(this.beforeNewProcessCreated).orElse(EMPTY_RUNNABLE).run();
            Runtime.getRuntime().exec(getCommandLine(raw));
            Optional.ofNullable(beforeCurrentProcessTerminated).orElse(EMPTY_RUNNABLE).run();
            Optional.ofNullable(terminationPolicy).orElse(() -> System.exit(0)).run();
        } catch (IOException e) {
            throw new RestartException("Couldn't execute the starter command with the OS", e);
        }
    }

    private Optional<OSProcess> getAppProcess() {
        SystemInfo systemInfo = new SystemInfo();
        OperatingSystem operatingSystem = systemInfo.getOperatingSystem();
        int currentPID = operatingSystem.getProcessId();
        return Optional.ofNullable(operatingSystem.getProcess(currentPID));
    }

    private String getCommandLine(@NotNull OSProcess osProcess) {
        String commandLine = osProcess.getCommandLine();
        final PlatformEnum platform = SystemInfo.getCurrentPlatformEnum();
        if (platform == PlatformEnum.LINUX || platform == PlatformEnum.MACOSX)
            commandLine = commandLine.replace(NUL_CHAR, SPACE);
        return Optional.ofNullable(this.cmdModifier)
                .orElse(EMPTY_FUNCTION)
                .apply(commandLine);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private Runnable beforeNewProcessCreated;
        private Runnable beforeCurrentProcessTerminated;
        private Runnable terminationPolicy;

        private Function<String, String> cmdModifier;

        private Builder() {
        }

        public Builder beforeNewProcessCreated(Runnable beforeNewProcessCreated) {
            this.beforeNewProcessCreated = beforeNewProcessCreated;
            return this;
        }

        public Builder beforeCurrentProcessTerminated(Runnable beforeCurrentProcessTerminated) {
            this.beforeCurrentProcessTerminated = beforeCurrentProcessTerminated;
            return this;
        }

        public Builder terminationPolicy(Runnable terminationPolicy) {
            this.terminationPolicy = terminationPolicy;
            return this;
        }

        public Builder modifyCmd(Function<String, String> cmdModifier) {
            this.cmdModifier = cmdModifier;
            return this;
        }

        public ApplicationRestart build() {
            return new ApplicationRestart(this);
        }
    }
}
