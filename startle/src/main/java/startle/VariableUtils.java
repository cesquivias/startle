package startle;

import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.lang.model.element.VariableElement;

public class VariableUtils {
    public static String getNameFromStaticFinal(VariableElement element) {
        return Stream.of(element.getSimpleName().toString().split("_"))
                .skip(1)
                .map(n -> n.charAt(0) + n.substring(1).toLowerCase())
                .collect(Collectors.joining());
    }
}
