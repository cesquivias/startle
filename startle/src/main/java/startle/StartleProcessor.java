package startle;

import com.google.auto.service.AutoService;
import com.squareup.javapoet.JavaFile;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.NoType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

import startle.annotation.RequestExtra;
import startle.annotation.Startle;
import startle.writer.StartActivityWriter;

import static java.util.Collections.singleton;

@AutoService(Processor.class)
public class StartleProcessor extends AbstractProcessor {
    private Types typeUtils;
    private Elements elementUtils;
    private Filer filer;
    private Messager messager;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        typeUtils = processingEnv.getTypeUtils();
        elementUtils = processingEnv.getElementUtils();
        filer = processingEnv.getFiler();
        messager = processingEnv.getMessager();
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return singleton(Startle.class.getCanonicalName());
    }

    @Override
    public boolean process(Set<? extends TypeElement> set, RoundEnvironment roundEnvironment) {
        for (Element element : roundEnvironment.getElementsAnnotatedWith(Startle.class)) {
            if (element.getKind() != ElementKind.CLASS) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                        "@Startle annotation must be on a class", element);
                return true;
            }
            TypeElement typeElement = (TypeElement) element;
            if (!isTypeChildOf(typeElement, "android.app.Activity")) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                        "@Startle annotation must be on an Activity");
                return true;
            }

            try {
                StartActivityWriter startActivityWriter = new StartActivityWriter(
                        typeElement, getStaticFinalExtras(typeElement),
                        getInstanceExtras(typeElement),
                        elementUtils, typeUtils);
                JavaFile javaFile = startActivityWriter.getJavaFile();

                JavaFileObject fileObject = filer.createSourceFile(startActivityWriter.getSourceFileName());
                Writer writer = fileObject.openWriter();
                javaFile.writeTo(writer);
                writer.close();
            } catch (IOException|IllegalStateException e) {
                messager.printMessage(Diagnostic.Kind.ERROR, e.getMessage());
            }
        }

        return false;
    }

    /**
     * A valid field is an instance (non-static), non-private, non-final field
     * with a type that can be put into a Bundle. That includes all primitives
     * and any objects that implement either Serializable or Parcelable.
     *
     * @param typeElement The activity class element
     * @return A list of all valid instance fields that have values in extras
     * @throws IllegalStateException if an invalid field is found
     */
    private List<VariableElement> getInstanceExtras(TypeElement typeElement) {
        List<VariableElement> vars = new ArrayList<>();
        for (Element el : typeElement.getEnclosedElements()) {
            if (el.getAnnotation(RequestExtra.class) == null) {
                continue;
            }
            VariableElement var = (VariableElement) el;
            Set<Modifier> modifiers = var.getModifiers();
            if (modifiers.contains(Modifier.STATIC)
                    && modifiers.contains(Modifier.FINAL)) {
                // static final fields are handled by #getStaticFinalExtras
                continue;
            }
            Name name = el.getSimpleName();
            if (modifiers.contains(Modifier.STATIC)) {
                throw new IllegalStateException(name + " cannot be static");
            }
            if (modifiers.contains(Modifier.FINAL)) {
                throw new IllegalStateException(name + " cannot be final");
            }
            if (modifiers.contains(Modifier.PRIVATE)) {
                throw new IllegalStateException(name + " cannot be private");
            }
            if (el.asType().getKind() == TypeKind.DECLARED &&
                    !typeUtils.isSubtype(el.asType(), elementUtils.getTypeElement("java.io.Serializable").asType())
                    && !typeUtils.isSubtype(el.asType(), elementUtils.getTypeElement("android.os.Parcelable").asType())) {
                throw new IllegalStateException(name + " class must be either Serializable or Parcelable");
            }
            vars.add(var);
        }
        return vars;
    }

    private List<VariableElement> getStaticFinalExtras(TypeElement typeElement) {
        TypeMirror stringType = elementUtils.getTypeElement("java.lang.String").asType();
        List<VariableElement> extras = new ArrayList<>();
        for (Element el : typeElement.getEnclosedElements()) {
            if (el.getAnnotation(RequestExtra.class) == null) {
                continue;
            }
            VariableElement var = (VariableElement) el;
            if (!var.getModifiers().contains(Modifier.STATIC)
                    || !var.getModifiers().contains(Modifier.FINAL)) {
                continue;
            }
            Name name = el.getSimpleName();
            if (!typeUtils.isSameType(var.asType(), stringType)) {
                throw new IllegalStateException(name + " must be a String");
            }
            extras.add(var);
        }
        return extras;
    }

    private boolean isTypeChildOf(TypeElement typeElement, String canonicalClassName) {
        TypeElement current = typeElement;
        NoType noType = typeUtils.getNoType(TypeKind.NONE);
        while(true) {
            TypeMirror superclass = current.getSuperclass();
            if (superclass.equals(noType)) {
                return false;
            }
            current = (TypeElement) ((DeclaredType) superclass).asElement();
            if (current.getQualifiedName().toString().equals(canonicalClassName)) {
                return true;
            }
        }
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.RELEASE_7;
    }
}
