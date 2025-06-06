package ai.wanaku.cli.main.support;

import jakarta.ws.rs.core.Response;

public final class ResponseHelper {
    private ResponseHelper() {}

    public static void commonResponseErrorHandler(Response response) {
        if (response.getStatus() == Response.Status.INTERNAL_SERVER_ERROR.getStatusCode()) {
            System.err.printf("The server was unable to handle the request: %s%n",
                    response.getStatusInfo().getReasonPhrase());

            System.exit(2);
        } else {
            System.err.printf("Unspecified error (status: %d, reason: %s)%n",
                    response.getStatus(), response.getStatusInfo().getReasonPhrase());

            System.exit(2);
        }
    }
}
