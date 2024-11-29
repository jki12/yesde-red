package common.exception;

public class HttpServerException extends RuntimeException {
    public HttpServerException(String message) {
        super(message);
    }
}
