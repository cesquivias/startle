package startle.writer;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeSpec;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

public class StartActivityWriter {
    private static final String PREFIX = "Start";

    private final TypeElement classElement;
    private final @Nonnull List<VariableElement> staticFinalExtras;
    private final @Nonnull List<VariableElement> instanceExtras;
    private final Elements elementUtils;
    private final Types typeUtils;
    private final String packageName;
    private final String startName;
    private final ParameterSpec contextParam;
    private final ClassName builderName;
    private final ClassName intentName;
    private final ClassName className;

    public StartActivityWriter(TypeElement classElement,
            @Nonnull List<VariableElement> staticFinalExtras,
            @Nonnull List<VariableElement> instanceExtras,
            Elements elementUtils, Types typeUtils) {
        this.classElement = classElement;
        this.staticFinalExtras = staticFinalExtras;
        this.instanceExtras = instanceExtras;
        this.elementUtils = elementUtils;
        this.typeUtils = typeUtils;
        this.packageName = getPackageName(classElement);
        this.startName = PREFIX + classElement.getSimpleName();
        this.contextParam = ParameterSpec.builder(
                ClassName.get("android.content", "Context"),
                "context")
                .build();
        builderName = ClassName.get(startName, "Builder");
        intentName = ClassName.get("android.content", "Intent");
        className = ClassName.get(classElement);
    }

    public JavaFile getJavaFile() {
        return JavaFile.builder(packageName, getClassSpec())
                .build();
    }

    public String getSourceFileName() {
        return packageName + "." + startName;
    }

    private TypeSpec getClassSpec() {
        List<TypeSpec> innerClasses = new ArrayList<>();
        List<MethodSpec> methods = new ArrayList<>();
        if (staticFinalExtras.isEmpty() && instanceExtras.isEmpty()) {
            methods.add(createBasicStartActivityMethod());
        } else {
            BuilderWriter builderWriter = new BuilderWriter(classElement,
                    staticFinalExtras, instanceExtras, elementUtils, typeUtils, builderName);
            innerClasses.add(builderWriter.createBuilder());
            methods.add(createPrepareMethod());
        }
        return TypeSpec.classBuilder(startName)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addTypes(innerClasses)
                .addMethods(methods)
                .build();
    }

    private MethodSpec createBasicStartActivityMethod() {
        return MethodSpec.methodBuilder("start" + classElement.getSimpleName())
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addParameter(contextParam)
                .addStatement("$1T intent = new $1T($3N, $2T.class)",
                        intentName,
                        className,
                        contextParam)
                .addStatement("context.startActivity(intent)")
                .build();
    }

    private MethodSpec createPrepareMethod() {
        return MethodSpec.methodBuilder("prepare" + classElement.getSimpleName())
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(builderName)
                .addParameter(contextParam)
                .addStatement("return new $T($N)", builderName, contextParam)
                .build();
    }

    private String getPackageName(TypeElement classElement) {
        return elementUtils.getPackageOf(classElement)
                .getQualifiedName().toString();
    }
}
