package exception;

public class UnsupportedHttpMethodException extends RuntimeException {
    private static final String MESSAGE = "지원하지 않는 HTTP METHOD 입니다.";

    public UnsupportedHttpMethodException() {
        super(MESSAGE);
    }
}
