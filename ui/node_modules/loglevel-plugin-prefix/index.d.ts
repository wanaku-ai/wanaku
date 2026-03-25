declare module 'loglevel-plugin-prefix' {
    import { Logger } from 'loglevel';

    interface LoglevelPluginPrefixOptions {
        template?: string;
        levelFormatter?: (level: string) => string;
        nameFormatter?: (name: string | undefined) => string;
        timestampFormatter?: (date: Date) => string;
        format?: (level: string, name: string | undefined, timestamp: Date) => string | undefined;
    }

    function reg(loglevel: Logger): void;
    function apply(logger: Logger, options?: LoglevelPluginPrefixOptions): Logger;
}
