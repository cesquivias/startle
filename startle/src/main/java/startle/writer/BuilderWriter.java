package startle.writer;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import startle.annotation.RequestExtra;

import static java.lang.Character.toLowerCase;
import static java.lang.Character.toUpperCase;
import static java.util.stream.Collectors.toList;

class BuilderWriter {

    private final TypeElement classElement;
    private final List<VariableElement> staticFinalExtras;
    private final List<VariableElement> instanceExtras;
    private final Types typeUtils;
    private final ClassName className;
    final ClassName builderName;
    private final ClassName intentName;
    private final ClassName contextName;
    private final ParameterSpec contextParam;
    private final TypeMirror requestExtraType;

    BuilderWriter(TypeElement classElement,
            List<VariableElement> staticFinalExtras, List<VariableElement> instanceExtras,
            Elements elementUtils, Types typeUtils, ClassName builderName) {
        this.classElement = classElement;
        this.staticFinalExtras = staticFinalExtras;
        this.instanceExtras = instanceExtras;
        this.typeUtils = typeUtils;
        this.className = ClassName.get(classElement);
        this.builderName = builderName;
        this.intentName = ClassName.get("android.content", "Intent");
        this.contextName = ClassName.get("android.content", "Context");
        this.contextParam = ParameterSpec.builder(contextName, "context")
                .build();
        requestExtraType = elementUtils.getTypeElement(RequestExtra.class.getCanonicalName()).asType();
    }

    TypeSpec createBuilder() {
        List<FieldSpec> fields = new ArrayList<>();
        FieldSpec contextField = FieldSpec.builder(contextName, "context")
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                .build();
        fields.add(contextField);
        List<MethodSpec> methods = new ArrayList<>();
        methods.add(MethodSpec.constructorBuilder()
                .addParameter(contextParam)
                .addStatement("this.$N = $N", contextField, contextParam)
                .build());
        CodeBlock.Builder verifyExtraBlocks = CodeBlock.builder();
        CodeBlock.Builder setExtraBlock = CodeBlock.builder();
        processStaticFinalFields(fields, methods, verifyExtraBlocks, setExtraBlock);
        processInstanceFields(fields, methods, verifyExtraBlocks, setExtraBlock);

        MethodSpec getIntentMethod = MethodSpec.methodBuilder("getIntent")
                .addModifiers(Modifier.PRIVATE)
                .returns(intentName)
                .addCode(verifyExtraBlocks.build())
                .addStatement("$1T intent = new $1T($2N, $3T.class)", intentName,
                        contextField, classElement)
                .addCode(setExtraBlock.build())
                .addStatement("return intent")
                .build();
        methods.add(getIntentMethod);
        methods.add(MethodSpec.methodBuilder("start")
                .addModifiers(Modifier.PUBLIC)
                .addStatement("$N.startActivity($N())", contextField, getIntentMethod)
                .build());
        methods.add(MethodSpec.methodBuilder("startForResult")
                .addModifiers(Modifier.PUBLIC)
                .addParameter(int.class, "requestCode")
                .addStatement("(($T) $N).startActivityForResult($N(), $N)",
                        ClassName.get("android.app", "Activity"),
                        contextField, getIntentMethod, "requestCode")
                .build());
        return TypeSpec.classBuilder(builderName)
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addFields(fields)
                .addMethods(methods)
                .build();
    }

    private void processInstanceFields(List<FieldSpec> fields, List<MethodSpec> methods,
            CodeBlock.Builder verifyExtraBlocks, CodeBlock.Builder setExtraBlock) {
        for (VariableElement extra : instanceExtras) {
            RequestExtra annotation = extra.getAnnotation(RequestExtra.class);
            // TODO : verify value not set
            String name = extra.getSimpleName().toString();
            TypeName extraClassName = getExtraFieldName(extra.asType());
            FieldSpec nameField = getStaticFinalExtraFieldSpec(name);
            fields.add(nameField);

            List<AnnotationSpec> annotationsSpecs = getAnnotationsSpecs(extra);
            FieldSpec field = getFieldSpec(extraClassName, name, annotationsSpecs);
            fields.add(field);
            ParameterSpec param = ParameterSpec.builder(extraClassName, name)
                    .addAnnotations(annotationsSpecs)
                    .build();
            methods.add(createSetter(toUpperCase(name.charAt(0)) + name.substring(1),
                    field, param));
            if (isNullable(extra)) {
                setExtraBlock.beginControlFlow("if ($N != null)", field)
                        .addStatement("intent.putExtra($N, $N)",
                                nameField, field)
                        .endControlFlow();
            } else {
                addRequiredVerification(verifyExtraBlocks, extra, field);
                setExtraBlock.addStatement("intent.putExtra($N, $N)",
                        nameField, field);
            }
        }
    }

    private boolean isNullable(VariableElement extra) {
        return extra.getAnnotationMirrors().stream()
                .anyMatch(a -> "Nullable".equals(
                        a.getAnnotationType().asElement().getSimpleName().toString()));
    }

    FieldSpec getStaticFinalExtraFieldSpec(String name) {
        return FieldSpec.builder(ClassName.get(String.class),
                "EXTRA_" + name.toUpperCase(),
                Modifier.STATIC, Modifier.FINAL)
                .initializer("\"$L.$L\"", className, name)
                .build();
    }

    private void processStaticFinalFields(List<FieldSpec> fields, List<MethodSpec> methods,
            CodeBlock.Builder verifyExtraBlocks, CodeBlock.Builder setExtraBlock) {
        for (VariableElement extra : staticFinalExtras) {
            RequestExtra requestExtra = extra.getAnnotation(RequestExtra.class);
            TypeName extraClassName;
            try {
                extraClassName = ClassName.get(requestExtra.value());
            } catch (MirroredTypeException e) {
                extraClassName = getExtraFieldName(e.getTypeMirror());
            }
            String name = getNameFromStaticFinal(extra);
            String camelName = toLowerCase(name.charAt(0)) + name.substring(1);
            FieldSpec field = getFieldSpec(extraClassName, camelName, getAnnotationsSpecs(extra));
            fields.add(field);
            ParameterSpec param = ParameterSpec.builder(extraClassName, camelName)
                    .build();
            methods.add(createSetter(name, field, param));
            if (isNullable(extra)) {
                addOptionalPut(setExtraBlock, extra, field);
            } else {
                addRequiredVerification(verifyExtraBlocks, extra, field);
                addRequiredPut(setExtraBlock, extra, field);
            }
        }
    }

    private List<AnnotationSpec> getAnnotationsSpecs(VariableElement extra) {
        return extra
                .getAnnotationMirrors().stream()
                .filter(a -> !typeUtils.isSameType(a.getAnnotationType(), requestExtraType))
                .map(AnnotationSpec::get)
                .collect(toList());
    }

    private void addRequiredVerification(CodeBlock.Builder verifyExtraBlocks, VariableElement extra, FieldSpec field) {
        verifyExtraBlocks.add(CodeBlock.builder()
                .beginControlFlow("if ($N == null)", field)
                .addStatement("throw new IllegalStateException($S)",
                        extra.getSimpleName() + " must be set")
                .endControlFlow()
                .build());
    }

    private void addRequiredPut(CodeBlock.Builder setExtraBlock, VariableElement extra, FieldSpec field) {
        setExtraBlock.addStatement("intent.putExtra($T.$L, $N)",
                className, extra.getSimpleName(), field);
    }

    private void addOptionalPut(CodeBlock.Builder setExtraBlock, VariableElement extra, FieldSpec field) {
        setExtraBlock.beginControlFlow("if ($N != null)", field)
                .addStatement("intent.putExtra($T.$L, $N)",
                        className, extra.getSimpleName(), field)
                .endControlFlow();
    }

    private MethodSpec createSetter(String name, FieldSpec field, ParameterSpec param) {
        return MethodSpec.methodBuilder("set" + name)
                .addModifiers(Modifier.PUBLIC)
                .addParameter(param)
                .addStatement("this.$N = $N", field, param)
                .addStatement("return this")
                .returns(builderName)
                .build();
    }

    private FieldSpec getFieldSpec(TypeName extraClassName, String camelName,
            List<AnnotationSpec> annotationSpecs) {
        return FieldSpec.builder(extraClassName, camelName)
                .addAnnotations(annotationSpecs)
                .addModifiers(Modifier.PRIVATE)
                .build();
    }

    private TypeName getExtraFieldName(TypeMirror typeMirror) {
        TypeKind kind = typeMirror.getKind();
        if (!kind.isPrimitive()) {
            return ClassName.get(typeMirror);
        } else {
            return ClassName.get(typeUtils.boxedClass((PrimitiveType) typeMirror));
        }
    }

    private String getNameFromStaticFinal(VariableElement element) {
        return Stream.of(element.getSimpleName().toString().split("_"))
                .skip(1)
                .map(n -> n.charAt(0) + n.substring(1).toLowerCase())
                .collect(Collectors.joining());
    }
}
