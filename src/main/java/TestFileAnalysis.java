import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.model.resolution.TypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

import java.io.*;
import java.util.*;

public class TestFileAnalysis {
    String FILE_PATH = "";
    String ROOT_PATH = "D:/picasso/src/main/java/";//"src\\main\\java"
    static List<PackageDeclaration> packageList;
    static List<ImportDeclaration> importPackageList;
    static List<ClassOrInterfaceDeclaration> innerClassList = new ArrayList<>();
    static List<FieldDeclaration> globelVariableList = new ArrayList<>();
    MethodDeclaration beforeMethod = new MethodDeclaration();
    static List<MethodDeclaration> normalMethodList = new ArrayList<>();

    public TestFileAnalysis(String filePath) {
        this.FILE_PATH = filePath;
    }

    public CompilationUnit constructCompilationUnit(String code, String FILE_PATH) throws FileNotFoundException {
        CombinedTypeSolver combinedTypeSolver = new CombinedTypeSolver();
        TypeSolver javaParserTypeSolver = new JavaParserTypeSolver(new File(ROOT_PATH));
        combinedTypeSolver.add(javaParserTypeSolver);
        TypeSolver reflectionTypeSolver = new ReflectionTypeSolver();
        combinedTypeSolver.add(reflectionTypeSolver);
        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(combinedTypeSolver);
        StaticJavaParser.getConfiguration().setSymbolResolver(symbolSolver);

        return code == null ? StaticJavaParser.parse(new File(FILE_PATH)) : StaticJavaParser.parse(code);
    }

    public List<String> getOriginalTestFragment() throws IOException {
        List<String> originalTestFragmentClassList = new ArrayList<>();
        CompilationUnit cu = constructCompilationUnit(null, FILE_PATH);
        //获取package列表
        packageList = cu.findAll(PackageDeclaration.class);
        //获取所有import列表
        importPackageList = cu.findAll(ImportDeclaration.class);
        //获取所有内部类
        cu.findAll(ClassOrInterfaceDeclaration.class).stream().filter(coid -> !FILE_PATH.contains(coid.getName().toString())).forEach(innerClassList::add);
        //获取类变量列表（包含可能的内部类的变量）
        List<FieldDeclaration> fieldDeclarationList = cu.findAll(FieldDeclaration.class);
        fieldDeclarationList.forEach(fd -> {
            int indexOfPublic = fd.getParentNode().get().toString().indexOf("public");
            int indexOfBraces = fd.getParentNode().get().toString().indexOf("{");
            if (indexOfPublic < indexOfBraces) {
                globelVariableList.add(fd);
            }
        });

        //获取非测试方法
        cu.findAll(MethodDeclaration.class).stream().filter(md -> !md.getAnnotationByName("Test").isPresent()).forEach(normalMethodList::add);
        //获取before方法
        for (MethodDeclaration md : normalMethodList) {
            if (md.getAnnotationByName("Before").isPresent()) {
                beforeMethod = md;
            }
        }
        //获取测试方法。先获取所有测试方法，然后转成类
        cu.findAll(MethodDeclaration.class).stream().filter(md -> md.getAnnotationByName("Test").isPresent()).forEach(md -> originalTestFragmentClassList.add("class TestFragment{\n" + md.toString() + "}"));
        return originalTestFragmentClassList;
    }

    public void dependencyAnalysis(String testFragmentString) throws IOException {
        List<String> externalVariableDependency = new ArrayList<>();
        List<FieldDeclaration> externalFieldDeclarationList = new ArrayList<>();
        List<String> fragmentContent = new ArrayList<>();
        List<String> methodDependency = new ArrayList<>();
        List<MethodDeclaration> methodDeclarationList = new ArrayList<>();
        Set<MethodDeclaration> methodDeclarationSet = new HashSet<>();
        MethodDeclaration targetMethodDeclaration = null;

        //获得compilationUnit，用于后续分析
        CompilationUnit cu = constructCompilationUnit(testFragmentString, null);
        //把全局变量扔进变量列表，以后还会根据方法中新的参数列表新增.第一个是变量名，第二个是变量类型。
        List<VariableTuple> variableList = new ArrayList<>();
        for (FieldDeclaration fd : globelVariableList) {
            variableList.add(new VariableTuple(true, fd.getVariable(0).getTypeAsString(), fd.getVariable(0).getNameAsString(), fd));
        }
        //获取所有调用方法中的变量.若第一项为true，则表示该变量是全局变量
        Map<Expression, Boolean> normalArgumentMap = new HashMap<>();
        Map<Expression, Boolean> abnormalArgumentMap = new HashMap<>();
        cu.findAll(MethodCallExpr.class).forEach(mce -> {
            NodeList<Expression> argumentList = mce.getArguments();
            for (Expression expression : argumentList) {
                try {
                    expression.calculateResolvedType().describe();
                    normalArgumentMap.put(expression, true);
                } catch (Exception e) {
                    if (e.getMessage().contains(" in ")) {
                        abnormalArgumentMap.put(expression, true);
                    } else {
                        abnormalArgumentMap.put(expression, false);
                    }
                }
            }
        });
        //获取所有构造方法中的变量
        cu.findAll(ObjectCreationExpr.class).forEach(oce -> {
            NodeList<Expression> argumentList = oce.getArguments();
            for (Expression expression : argumentList) {
                try {
                    expression.calculateResolvedType().describe();
                    normalArgumentMap.put(expression, true);
                } catch (Exception e) {
                    if (e.getMessage().contains(" in ")) {
                        abnormalArgumentMap.put(expression, true);
                    } else {
                        abnormalArgumentMap.put(expression, false);
                    }
                }
            }
        });
        //初始化ClassOrInterfaceDeclaration,用于构造新的测试片段类
        ClassOrInterfaceDeclaration myClass = new ClassOrInterfaceDeclaration();
        //获取MethodDeclanation，用于后续添加语句
        MethodDeclaration myMethod = cu.findAll(MethodDeclaration.class).get(0);
        //获取第三方变量，处理import
        myClass.setName("TestFragment");
        Set<String> importTypeSet = new HashSet<>();
        cu.findAll(VariableDeclarator.class).forEach(v -> importTypeSet.add(v.getTypeAsString()));

        Map<Expression, Boolean> tempAbnormalArgumentMap = new HashMap<>(abnormalArgumentMap);
        for (Map.Entry<Expression, Boolean> entry : abnormalArgumentMap.entrySet()) {
            Expression var = entry.getKey();
            boolean isGlobal = entry.getValue();
            for (VariableTuple vt : variableList) {
                if (var.toString().equals(vt.name) && (isGlobal == vt.isGlobal)) {
                    if (vt.original.getClass().equals(FieldDeclaration.class)) {
                        FieldDeclaration fd = (FieldDeclaration) vt.original;
                        myClass.addMember(fd);
                        fragmentContent.add(fd.toString());
                        importTypeSet.add(fd.getElementType().toString());
                        tempAbnormalArgumentMap.remove(var);
                    }
                }
            }
        }
        //获取所有函数
        List<MethodCallExpr> methodCallList = cu.findAll(MethodCallExpr.class);
        List<VariableDeclarator> variableDeclarators = cu.findAll(VariableDeclarator.class);


        for (Map.Entry<Expression, Boolean> entry : tempAbnormalArgumentMap.entrySet()) {
            if (entry.getValue() == true) {
                importTypeSet.add(entry.getKey().toString());
            }
        }
        //处理import
        List<ImportDeclaration> importList = new ArrayList<>();
        for (String s : importTypeSet) {
            if (getImportDeclartion(importPackageList, s) != null) {
                importList.add(getImportDeclartion(importPackageList, s));
            }
        }

        List<String> writeFileImportList = new ArrayList<>();
        Map<String, String> importMap = new HashMap<>();
        for (ImportDeclaration i : importList) {
            String path = "D:/picasso/src/test/java/" + i.getNameAsString().replaceAll("\\.", "/");
            String newPath = findFile(path);
            //暂时不考虑非变量import，直接提供完整的import列表
            if (newPath.length() == 0) {
                writeFileImportList.add(i.toString());
            }
            if (newPath.length() > 0) {
                String tempPath = newPath.replaceAll("\\.java", "");
                String variable = path.replaceAll(tempPath + "/", "");
                importMap.put(variable, newPath);
            }
        }

        for (Map.Entry<String, String> entry : importMap.entrySet()) {
            Map<FieldDeclaration, ImportDeclaration> declarationMap = findVariable(entry.getKey(), entry.getValue());
            FieldDeclaration f = new FieldDeclaration();
            ImportDeclaration i = null;
            for (Map.Entry<FieldDeclaration, ImportDeclaration> entry1 : declarationMap.entrySet()) {
                f = entry1.getKey();
                i = entry1.getValue();
            }
            if (i != null) {
                writeFileImportList.add(i.toString());
            }
            myClass.addMember(f);
            externalFieldDeclarationList.add(f);
            externalVariableDependency.add(f.toString());
        }

        for (MethodCallExpr mce : methodCallList) {
            if (!mce.toString().contains("assert")) {
                MethodDeclaration method = getTargetMethod(importList, mce, variableDeclarators, externalFieldDeclarationList);
                if (method != null) {
                    methodDependency.add(method.toString());
                    methodDeclarationList.add(method);
                    methodDeclarationSet.add(method);
                }
            }
        }

        //构造类型
        NodeList<Parameter> parameters = myMethod.getParameters();
        List<String> typeList = new ArrayList<>();
        for (Parameter p : parameters) {
            typeList.add(p.getTypeAsString());
        }
        String typeString = "(";
        if (typeList.size() > 0) {
            String s1 = typeList.get(0).replaceAll("\\?", "");
            s1 = s1.replaceAll("<", "");
            s1 = s1.replaceAll(">", "");
            typeString += s1;
            for (int i = 1; i < typeList.size(); i++) {
                String s2 = typeList.get(i).replaceAll("\\?", "");
                s2 = s2.replaceAll("<", "");
                s2 = s2.replaceAll(">", "");
                typeString = typeString + "," + s2;
            }

        }
        typeString += ")";

        /**
         * assertArrayEquals
         * assertEquals
         * assertFalse
         * assertNotEquals
         * assertNotNull
         * assertNotSame
         * assertNull
         * assertSame
         * assertThat
         * assertThrows
         * assertTrue
         */
        myClass.addMember(beforeMethod);
        //添加@Test
        myClass.addMember(myMethod);

        for (MethodDeclaration md : methodDeclarationSet) {
            myClass.addMember(md);
        }
        //添加测试目标方法

        String methodName = myMethod.getNameAsString();
        //给新的写入文件添加测试方法语句
        List<String> methodContent = new ArrayList<>();
        myMethod.getBody().get().getStatements().forEach(s -> methodContent.add(s.toString()));
        methodContent.forEach(m -> fragmentContent.add(m));
        String packageName = packageList.get(0).getNameAsString();
        System.out.println(myClass.toString());
        //写文件
        writeTestClass(packageName, methodName, typeString, writeFileImportList, myClass);
//        writeTestFragment(packageName, methodName, typeString, writeFileImportList, externalVariableDependency, fragmentContent);
    }

    public MethodDeclaration getTargetMethod(List<ImportDeclaration> importList, Expression expression, List<VariableDeclarator> variableDeclarators, List<FieldDeclaration> externalFieldDeclarationList) throws FileNotFoundException {
        MethodDeclaration targetMethodDeclaration = null;
        if (expression.asMethodCallExpr().getScope().isPresent()) {
            //如果是对象+调用方法
            Expression object = expression.asMethodCallExpr().getScope().get();
            String methodName = expression.asMethodCallExpr().getNameAsString();
            List<Expression> arguments = expression.asMethodCallExpr().getArguments();
            List<String> newTypeList = getVariableTypeList(arguments, variableDeclarators, externalFieldDeclarationList);
            if (Character.isUpperCase(object.toString().charAt(0))) {
                //如果调用该方法的对象首字母是大写，说明是类class.xxx()，查看import
                ImportDeclaration id = getImportDeclartion(importPackageList, object.toString());
                if(id!=null){
                    String packageName = getImportDeclartion(importPackageList, object.toString()).getNameAsString().split("." + object.toString())[0];
                    targetMethodDeclaration = getExternalMethod(methodName, packageName, object.toString(), newTypeList);
                }
            } else {
                //如果调用该方法的对象首字母是小写，而且是对象+方法调用：xxx.xxx()，查看它是什么类型（暂时认为不是基础类型）
                if (!object.isMethodCallExpr()) {
                    //如果不是xxx().xxx(),而是xxx.xxx().此处假设类型就是类名
                    VariableDeclarator vd = getVariableInitialize(object, globelVariableList, variableDeclarators, externalFieldDeclarationList);
                    if(vd!=null){
                        String className = getVariableInitialize(object, globelVariableList, variableDeclarators, externalFieldDeclarationList).getType().toString();
                        ImportDeclaration id = getImportDeclartion(importPackageList, className);
                        if (id != null) {//保证提取的是类
                            String packageName = id.getNameAsString().split("." + className)[0];
                            targetMethodDeclaration = getExternalMethod(methodName, packageName, className, newTypeList);
                        }
                    }
                }
            }
        } else {
            //如果不是对象+调用方法
            for (MethodDeclaration md : normalMethodList) {//如果是本文件的方法
                if (md.getName() == expression.asMethodCallExpr().getName()) {
                    targetMethodDeclaration = md;
                }
            }
            if (targetMethodDeclaration == null) {
                //说明调用的方法import的
                String methodName = expression.asMethodCallExpr().getNameAsString();
                ImportDeclaration id = getImportDeclartion(importPackageList, methodName);
                if(id!=null){
                    String importString = id.getNameAsString().split("." + methodName)[0];
                    String className = importString.split("\\.")[importString.split("\\.").length - 1];
                    String packageName = importString.split("." + className)[0];
                    List<Expression> arguments = expression.asMethodCallExpr().getArguments();
                    List<String> newTypeList = getVariableTypeList(arguments, variableDeclarators, externalFieldDeclarationList);
                    targetMethodDeclaration = getExternalMethod(methodName, packageName, className, newTypeList);
                }
            }
        }
        return targetMethodDeclaration;
    }

    public List<String> getVariableTypeList(List<Expression> arguments, List<VariableDeclarator> variableDeclarators, List<FieldDeclaration> externalFieldDeclarationList) {
        List<String> variableTypeList = new ArrayList<>();
        for (Expression expression1 : arguments) {
            VariableDeclarator vd2 = getVariableInitialize(expression1, globelVariableList, variableDeclarators, externalFieldDeclarationList);
            if (vd2 != null) {
                variableTypeList.add(vd2.getTypeAsString());
            } else {
                variableTypeList.add(getBasicDataType(expression1));
            }
        }
        return variableTypeList;
    }

    public MethodDeclaration getExternalMethod(String targetMethodCallName, String packageName, String className, List<String> typeList) throws FileNotFoundException {
        //targetMethodCallName就是方法名
        MethodDeclaration externalMethod = null;
        List<String> MUTList = new ArrayList<>();
        MUTList = findFileList(new File("MUTClass/"), MUTList);
        for (String s : MUTList) {
            s = s.split("\\\\")[1];
            String path = packageName + "+" + className + "+" + targetMethodCallName;
            if (s.contains(path)) {
                if (judgeArguments(s, typeList)) {
                    CompilationUnit cu2 = constructCompilationUnit(null, "MUTClass/" + s);
                    externalMethod = cu2.findAll(MethodDeclaration.class).get(0);
                }
            }
        }
        return externalMethod;
    }

    public ImportDeclaration getImportDeclartion(List<ImportDeclaration> importDeclarations, String objectType) {
        for (ImportDeclaration id : importDeclarations) {
            if (id.getNameAsString().contains("." + objectType)) {
                //这里就需要对import进行处理
                String[] s = id.getNameAsString().split("." + objectType);
                //如果分割后，前一个不是字母，后一个不是字母
                if (s.length == 1) {
                    return id;
                } else if (!String.valueOf(s[1].charAt(0)).matches("[a-zA-Z]+")) {
                    return id;
                }
            }
        }
        return null;
    }

    public String getBasicDataType(Expression expression) {
        String type = null;
        if (expression.isIntegerLiteralExpr()) {
            type = "int";
        } else if (expression.isLongLiteralExpr()) {
            type = "long";
        } else if (expression.isCharLiteralExpr()) {
            type = "char";
        } else if (expression.isDoubleLiteralExpr()) {
            type = "double";
        } else if (expression.isStringLiteralExpr()) {
            type = "String";
        } else if (expression.isBooleanLiteralExpr()) {
            type = "boolean";
        }
        return type;
    }

    public VariableDeclarator getVariableInitialize(Expression expression, List<FieldDeclaration> fieldDeclarations, List<VariableDeclarator> variableDeclarators, List<FieldDeclaration> externalFieldDeclarationList) {
        VariableDeclarator result = null;
        for (VariableDeclarator vd : variableDeclarators) {
            if (vd.getNameAsExpression().equals(expression)) {
                result = vd;
            }
        }
        for (FieldDeclaration fd : fieldDeclarations) {
            if (fd.getVariable(0).getNameAsExpression().equals(expression)) {
                result = fd.getVariable(0);
            }
        }
        for (FieldDeclaration fd : externalFieldDeclarationList) {
            if (fd.getVariable(0).getNameAsExpression().equals(expression)) {
                result = fd.getVariable(0);
            }
        }
        return result;
    }

    //获取目录下所有文件
    public List<String> findFileList(File dir, List<String> fileNames) {
        String[] files = dir.list();// 读取目录下的所有目录文件信息
        if (files != null) {
            for (String s : files) {// 循环，添加文件名或回调自身
                File file = new File(dir, s);
                if (file.isFile()) {// 如果文件
                    fileNames.add(dir + "\\" + file.getName());// 添加文件全路径名
                } else {// 如果是目录
                    findFileList(file, fileNames);// 回调自身继续查询
                }
            }
        }
        return fileNames;
    }

    public boolean judgeArguments(String filepath, List<String> arguments) {
        boolean flag = true;
        for (String s : arguments) {
            if (s != null) {//忽略类型为null的
                if (!filepath.contains(s)) {
                    flag = false;
                    break;
                }
            }
        }
        return flag;
    }

    public void writeTestClass(String packageName, String methodName, String typeString, List<String> writeFileImportList, ClassOrInterfaceDeclaration myClass) {
        try {
            String[] filenameArray = FILE_PATH.split("/");
            String filename = filenameArray[filenameArray.length - 1].split("\\.")[0];
            String outputFileName = "Test Class/" + packageName + "+" + filename + "+" + methodName + "+" + typeString + ".txt";
            BufferedWriter bw = new BufferedWriter(new FileWriter(outputFileName));
            for (String s : writeFileImportList) {
                bw.write(s);
            }
            bw.write(myClass.toString());
            bw.close();
            System.err.println("文件写入成功");
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

//    public void writeTestFragment(String packageName, String methodName, String typeString, List<String> writeFileImportList, List<String> externalVariableDependency, List<String> fragmentContent) {
//        try {
//            String[] filenameArray = FILE_PATH.split("/");
//            String filename = filenameArray[filenameArray.length - 1].split("\\.")[0];
//            String outputFileName = "Test Fragment/" + packageName + "_" + filename + "_" + methodName + ".txt";
//            BufferedWriter bw = new BufferedWriter(new FileWriter(outputFileName));
//            bw.write("test fragment:{\n");
//            bw.write("\timport dependency:{\n");
//            for (String s : writeFileImportList) {
//                bw.write("\t\t" + s);
//            }
////            for(ImportDeclaration i: importPackageList){
////                bw.write("\t\t"+i.toString());
////            }
//            bw.write("\t}\n}");
//            bw.write("\texternal variable dependency:{\n");
//            for (String s : externalVariableDependency) {
//                bw.write("\t\t" + s + "\n");
//            }
//            bw.write("\t},\n");
//            bw.write("\tfragment content:{\n");
//            for (String s : fragmentContent) {
//                bw.write("\t\t" + s + "\n");
//            }
//            bw.write("\t},\n");
//
//            bw.close();
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }

    public EnumDeclaration findEnum(String variable, String filePath) throws FileNotFoundException {
        CompilationUnit cu = constructCompilationUnit(null, filePath);
        List<EnumDeclaration> enumDeclarationList = cu.findAll(EnumDeclaration.class);
        for (EnumDeclaration ed : enumDeclarationList) {
            if (variable.contains(ed.getName().toString())) {
                return ed;
            }
        }
        return null;
    }

    public Map<FieldDeclaration, ImportDeclaration> findVariable(String variable, String filePath) throws FileNotFoundException {
        CompilationUnit cu = constructCompilationUnit(null, filePath);
        List<FieldDeclaration> newfieldDeclarationList = cu.findAll(FieldDeclaration.class);
        List<ImportDeclaration> newImportDeclarationList = cu.findAll(ImportDeclaration.class);
        Set<String> newImportTypeSet = new HashSet<>();
        ImportDeclaration resultImportDeclaration = null;
        FieldDeclaration resultFieldDeclaration = new FieldDeclaration();
        for (FieldDeclaration fd : newfieldDeclarationList) {
            List<VariableDeclarator> variableDeclaratorList = fd.getVariables();
            for (VariableDeclarator vd : variableDeclaratorList) {
                if (vd.getNameAsString().equals(variable)) {
                    newImportTypeSet.add(fd.getElementType().toString());
                    resultFieldDeclaration = fd;
                }
            }
        }
        for (String s : newImportTypeSet) {
            for (ImportDeclaration id : newImportDeclarationList) {
                String[] importNameArray = id.getNameAsString().split("\\.");
                String importName = importNameArray[importNameArray.length - 1];
                if (importName.equals(s)) {
                    resultImportDeclaration = id;
                }
            }
        }
        Map<FieldDeclaration, ImportDeclaration> resultMap = new HashMap<>();
        resultMap.put(resultFieldDeclaration, resultImportDeclaration);
        return resultMap;
    }

    public String findFile(String sourcePath) {
        String filePath = sourcePath + ".java";
        File file = new File(filePath);
        String[] pathArray = sourcePath.split("/");
        if (file.exists()) return filePath;
        if (pathArray.length == 1) return "";
        String newPathString = "";
        if (!file.exists() && pathArray.length > 1) {
            StringBuilder newPath = new StringBuilder();
            for (int i = 0; i < pathArray.length - 1; i++) {
                newPath.append(pathArray[i]).append("/");
            }
            newPathString = newPath.toString().substring(0, newPath.length() - 1);
        }
        return findFile(newPathString);
    }
}

