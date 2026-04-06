package ai.wanaku.tool.exec;

import jakarta.inject.Singleton;

import ai.wanaku.capabilities.sdk.common.ProcessRunner;

@Singleton
final class ProcessRunnerCommandExecutor implements CommandExecutor {
    @Override
    public Object run(String[] command) {
        return ProcessRunner.runWithOutput(command);
    }
}
