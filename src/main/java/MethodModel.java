import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;

import java.util.ArrayList;
import java.util.List;

public class MethodModel {
    private String packageName;
    private String className;
    private String methodName;
    private String parameterTypeList;
    private MethodDeclaration methodBody;
    private ConstructorDeclaration constructorBody;

    public MethodModel(String packageName, String className, String methodName, List<String> typeList, MethodDeclaration methodBody) {
        this.packageName = packageName;
        this.className = className;
        this.methodName = methodName;
        this.methodBody = methodBody;
        if (typeList.size() == 0) {
            this.parameterTypeList = "()";
        } else {
            List<Parameter> parameterList = methodBody.getParameters();
            List<String> parameters = new ArrayList<>();
            for (Parameter p : parameterList) {
                parameters.add(p.getTypeAsString());
            }
            StringBuilder s = new StringBuilder("(" + parameters.get(0));
            for (int i = 1; i < parameters.size(); i++) {
                s.append(",").append(parameters.get(i));
            }
            s.append(")");
            this.parameterTypeList = s.toString();
        }
    }

    public MethodModel(String packageName, String className, String methodName, List<String> typeList, ConstructorDeclaration methodBody) {
        this.packageName = packageName;
        this.className = className;
        this.methodName = methodName;
        this.constructorBody = methodBody;
        if (typeList.size() == 0) {
            this.parameterTypeList = "()";
        } else {
            List<Parameter> parameterList = methodBody.getParameters();
            List<String> parameters = new ArrayList<>();
            for (Parameter p : parameterList) {
                parameters.add(p.getTypeAsString());
            }
            StringBuilder s = new StringBuilder("(" + parameters.get(0));
            for (int i = 1; i < parameters.size(); i++) {
                s.append(",").append(parameters.get(i));
            }
            s.append(")");
            this.parameterTypeList = s.toString();
        }
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public MethodDeclaration getMethodBody() {
        return methodBody;
    }

    public void setMethodBody(MethodDeclaration methodBody) {
        this.methodBody = methodBody;
    }

    public String getParameterTypeList() {
        return parameterTypeList;
    }

    public void setParameterTypeList(String parameterTypeList) {
        this.parameterTypeList = parameterTypeList;
    }

    public ConstructorDeclaration getConstructorBody() {
        return constructorBody;
    }

    public void setConstructorBody(ConstructorDeclaration constructorBody) {
        this.constructorBody = constructorBody;
    }

    @Override
    public String toString() {
        return "MethodModel{" +
                "packageName='" + packageName + '\'' +
                ", className='" + className + '\'' +
                ", methodName='" + methodName + '\'' +
                ", parameterTypeList='" + parameterTypeList + '\'' +
                ", methodBody=" + methodBody +
                ", constructorBody=" + constructorBody +
                '}';
    }
}
