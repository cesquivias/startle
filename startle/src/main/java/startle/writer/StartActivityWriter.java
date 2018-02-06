package startle.writer;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import startle.VariableUtils;
import startle.annotation.RequestExtra;

import static startle.StringUtils.capitalize;

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
            if (!instanceExtras.isEmpty()) {
                methods.add(createSetExtrasMethod(builderWriter));
            }
             methods.addAll(createIntentGetterMethods());
        }
        List<VariableElement> extras = new ArrayList<>(staticFinalExtras);
        extras.addAll(instanceExtras);
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

    private MethodSpec createSetExtrasMethod(BuilderWriter builderWriter) {
        ParameterSpec activityParam = ParameterSpec.builder(className, "activity")
                .build();
        CodeBlock.Builder setBlock = CodeBlock.builder();
        for (VariableElement extra : instanceExtras) {
            FieldSpec extraField = FieldSpec.builder(ClassName.get(extra.asType()),
                    extra.getSimpleName().toString())
                    .build();
            FieldSpec extraStaticKeyField = builderWriter.getStaticFinalExtraFieldSpec(
                    extra.getSimpleName().toString());
            TypeKind kind = extra.asType().getKind();
            if (kind.isPrimitive()) {
                String type = extra.asType().toString();
                setBlock.addStatement("$N.$N = intent.get$LExtra($T.$N, $L)",
                        activityParam, extraField, capitalize(type),
                        builderWriter.builderName, extraStaticKeyField, getDefaultValue(kind));
            } else {
                setBlock.addStatement("$N.$N = ($T) intent.get$LExtra($T.$N)",
                        activityParam, extraField, ClassName.get(extra.asType()),
                        getExtraTypeName(extra.asType()),
                        builderWriter.builderName, extraStaticKeyField);
            }
        }
        return MethodSpec.methodBuilder("setExtras")
                .addModifiers(Modifier.STATIC)
                .addParameter(activityParam)
                .addStatement("$T intent = $N.getIntent()", intentName, activityParam)
                .addCode(setBlock.build())
                .build();
    }

    private List<MethodSpec> createIntentGetterMethods() {
        List<MethodSpec> getters = new ArrayList<>();
        for (VariableElement staticField : staticFinalExtras) {
            RequestExtra requestExtra = staticField.getAnnotation(RequestExtra.class);
            TypeMirror extraType;
            try {
                extraType = elementUtils.getTypeElement(requestExtra.value().getCanonicalName()).asType();
            } catch (MirroredTypeException e) {
                extraType = e.getTypeMirror();
            }
            TypeName returnClass = ClassName.get(extraType);
            getters.add(MethodSpec.methodBuilder("get" + capitalize(VariableUtils.getNameFromStaticFinal(staticField)))
                    .addModifiers(Modifier.STATIC)
                    .addParameter(intentName, "intent")
                    .addCode(getterBlock(staticField, extraType))
                    .returns(returnClass)
                    .build());
        }
        return getters;
    }

    private CodeBlock getterBlock(VariableElement var, TypeMirror type) {
        if (type.getKind().isPrimitive()) {
            return CodeBlock.builder()
                    .addStatement("return intent.get$LExtra($T.$N, $L)",
                            getExtraTypeName(type), className, var.getSimpleName(),
                            getDefaultValue(type.getKind()))
                    .build();
        } else {
            return CodeBlock.builder()
                    .addStatement("return ($T) intent.get$LExtra($T.$N)",
                            type, getExtraTypeName(type), className, var.getSimpleName())
                    .build();
        }
    }

    private Object getDefaultValue(TypeKind kind) {
        switch (kind) {
            case BOOLEAN:
                return false;
            case BYTE:
            case SHORT:
            case INT:
            case LONG:
            case CHAR:
                return -1;
            case FLOAT:
            case DOUBLE:
                return -1.0;
            default:
                return null;
        }
    }

    private String getExtraTypeName(TypeMirror type) {
        if (type.getKind().isPrimitive()) {
            String typeName = typeUtils.getPrimitiveType(type.getKind()).toString();
            return capitalize(typeName);
        } else if (typeUtils.isSubtype(type, elementUtils.getTypeElement("java.io.Serializable").asType())) {
            return "Serializable";
        } else if (typeUtils.isSubtype(type, elementUtils.getTypeElement("android.os.Parcelable").asType())) {
            return "Parcelable";
        } else {
            throw new IllegalStateException("Cannot find extra getter for type " + type);
        }
    }

    private String getPackageName(TypeElement classElement) {
        return elementUtils.getPackageOf(classElement)
                .getQualifiedName().toString();
    }
}
