package node.option;


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface OptionField {
    OptionInputType type() default OptionInputType.TEXT;
    boolean editable() default true;
    /** 편집기에 보여줄 이름입니다. 비어 있으면 필드명을 씁니다. */
    String label() default "";
    /** 숫자 필드가 가질 수 있는 최솟값입니다. 기본값은 제한 없음입니다. */
    long min() default Long.MIN_VALUE;
}
