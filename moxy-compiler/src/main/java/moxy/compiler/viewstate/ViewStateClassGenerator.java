package moxy.compiler.viewstate;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.DeclaredType;
import moxy.MvpProcessor;
import moxy.compiler.JavaFilesGenerator;
import moxy.compiler.MvpCompiler;
import moxy.compiler.Util;
import moxy.viewstate.MvpViewState;
import moxy.viewstate.ViewCommand;

import static moxy.compiler.Util.decapitalizeString;

public final class ViewStateClassGenerator extends JavaFilesGenerator<ViewInterfaceInfo> {

    private static final int COMMAND_FIELD_NAME_RANDOM_BOUND = 10;

    @Override
    public List<JavaFile> generate(ViewInterfaceInfo viewInterfaceInfo) {
        ClassName viewName = viewInterfaceInfo.getName();
        TypeName nameWithTypeVariables = viewInterfaceInfo.getNameWithTypeVariables();
        DeclaredType viewInterfaceType = (DeclaredType) viewInterfaceInfo.getElement().asType();

        String typeName = Util.getSimpleClassName(viewInterfaceInfo.getElement()) + MvpProcessor.VIEW_STATE_SUFFIX;
        TypeSpec.Builder classBuilder = TypeSpec.classBuilder(typeName)
            .addModifiers(Modifier.PUBLIC)
            .superclass(ParameterizedTypeName.get(ClassName.get(MvpViewState.class), nameWithTypeVariables))
            .addSuperinterface(nameWithTypeVariables)
            .addTypeVariables(viewInterfaceInfo.getTypeVariables());

        for (ViewMethod method : viewInterfaceInfo.getMethods()) {
            TypeSpec commandClass = generateCommandClass(method, nameWithTypeVariables);
            classBuilder.addType(commandClass);
            classBuilder.addMethod(generateMethod(viewInterfaceType, method, nameWithTypeVariables, commandClass));
        }

        JavaFile javaFile = JavaFile.builder(viewName.packageName(), classBuilder.build())
            .indent("\t")
            .build();
        return Collections.singletonList(javaFile);
    }

    private TypeSpec generateCommandClass(ViewMethod method, TypeName viewTypeName) {
        MethodSpec applyMethod = MethodSpec.methodBuilder("apply")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .addParameter(viewTypeName, "mvpView")
            .addExceptions(method.getExceptions())
            .addStatement("mvpView.$L($L)", method.getName(), method.getArgumentsString())
            .build();

        TypeSpec.Builder classBuilder = TypeSpec.classBuilder(method.getCommandClassName())
            .addModifiers(Modifier.PUBLIC) // TODO: private and static
            .addTypeVariables(method.getTypeVariables())
            .superclass(ParameterizedTypeName.get(ClassName.get(ViewCommand.class), viewTypeName))
            .addMethod(generateCommandConstructor(method))
            .addMethod(applyMethod);

        for (ParameterSpec parameter : method.getParameterSpecs()) {
            // TODO: private field
            classBuilder.addField(parameter.type, parameter.name, Modifier.PUBLIC, Modifier.FINAL);
        }

        return classBuilder.build();
    }

    private MethodSpec generateMethod(DeclaredType enclosingType, ViewMethod method,
        TypeName viewTypeName, TypeSpec commandClass) {
        // TODO: String commandFieldName = "$cmd";
        String commandFieldName = decapitalizeString(method.getCommandClassName());

        // Add salt if contains argument with same name
        Random random = new Random();
        while (method.getArgumentsString().contains(commandFieldName)) {
            commandFieldName += random.nextInt(COMMAND_FIELD_NAME_RANDOM_BOUND);
        }

        return MethodSpec.overriding(method.getElement(), enclosingType, MvpCompiler.getTypeUtils())
            .addStatement("$1N $2L = new $1N($3L)", commandClass, commandFieldName,
                method.getArgumentsString())
            .addStatement("viewCommands.beforeApply($L)", commandFieldName)
            .addCode("\n")
            .beginControlFlow("if (hasNotView())")
            .addStatement("return")
            .endControlFlow()
            .addCode("\n")
            .beginControlFlow("for ($T view : views)", viewTypeName)
            .addStatement("view.$L($L)", method.getName(), method.getArgumentsString())
            .endControlFlow()
            .addCode("\n")
            .addStatement("viewCommands.afterApply($L)", commandFieldName)
            .build();
    }

    private MethodSpec generateCommandConstructor(ViewMethod method) {
        List<ParameterSpec> parameters = method.getParameterSpecs();

        MethodSpec.Builder builder = MethodSpec.constructorBuilder()
            .addParameters(parameters)
            .addStatement("super($S, $T.class)", method.getTag(), method.getStrategy());

        if (parameters.size() > 0) {
            builder.addCode("\n");
        }

        for (ParameterSpec parameter : parameters) {
            builder.addStatement("this.$1N = $1N", parameter);
        }

        return builder.build();
    }
}
