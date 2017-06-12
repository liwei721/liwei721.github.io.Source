package net.sourceforge.pmd.lang.java.rule.androidreadline;

import net.sourceforge.pmd.lang.ast.Node;
import net.sourceforge.pmd.lang.java.ast.*;
import net.sourceforge.pmd.lang.java.rule.AbstractJavaRule;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by zlw on 2017/6/12.
 *
 * public class TestLog{
 static Logger Log = Logger.getLogger("log");
 static boolean DEBUG = true;
 static boolean DEBUG1 = false;
 public static void main(String []args){
 Context cont = activity.getApplicationContext();
 String classname = activity.getLocalClassName();
 String pcodeName = cont.getPackageCodePath();
 int id= android.os.Process.myPid();
 String pid =String.valueOf(id);
 int uicd= android.os.Process.myUid();
 String uid = String.valueOf(uicd);
 int idname= android.os.Process.getUidForName("pay");
 String imei = ((TelephonyManager)getSystemService(TELEPHONY_SERVICE)).getDeviceId();
 int bbq=activity.getLocalClassName();

 Log.i("classname", classname);//触发规则
 Log.i("pcodeName", pcodeName);//触发规则
 Log.i("pid", pid);//触发规则
 Log.i("uid", uid);//触发规则
 Log.i("imei", imei); //触发规则
 Log.i("imei", imei.length);
 Log.i("imei", imei.size());
 Log.i("imei:", activity.getLocalClassName());//触发规则
 Log.i("imei:", MYUUID);
 Log.i("imei:", imei.toString());//触发规则
 Log.i("imei:", ab.imei.toString());//触发规则
 Log.i("imei:", bbq);//触发规则
 Log.i("imei:", idname);//触发规则
 Log.i("imei:", id);//触发规则
 Log.i("imei:", uicd);//触发规则
 Log.i("imei:", pcodeName);//触发规则
 Log.i("imei:", 101);

 if (DEBUG) {
 Log.i("imei", imei);//触发规则
 }
 if (DEBUG1) {
 Log.i("imei", imei);
 }
 }
 }
 */
public class LogBlockRule extends AbstractJavaRule {
    private static Set<String> SensitiveStrings = new HashSet<String>();
    private List<ASTName> astNamewithLog = (List<ASTName>) new ArrayList<ASTName>();
    private List<String> BooleanStrings = new ArrayList<String>();
    private List<ASTName> SASTNames = (List<ASTName>) new ArrayList<ASTName>();
    private List<ASTVariableDeclaratorId> SensitiveVariables =(List<ASTVariableDeclaratorId>)new ArrayList<ASTVariableDeclaratorId>();

    static {
        SensitiveStrings.add("classname");
        SensitiveStrings.add("pid");
        SensitiveStrings.add("uid");
        SensitiveStrings.add("imei");
        SensitiveStrings.add("getLocalClassName");
        SensitiveStrings.add("getPackageCodePath");
        SensitiveStrings.add("getPackagePath");
        SensitiveStrings.add("android.os.Process.myPid");
        SensitiveStrings.add("android.os.Process.myUid");
        SensitiveStrings.add("android.os.Process.getUidForName");
    }


    @Override
    public Object visit(ASTCompilationUnit node, Object data) {
        return super.visit(node, data);
    }

    /**
     * 检查log是否有敏感信息输出
     *
     * @param node
     * @param data
     */
    private void checkLogRule(Node node, Object data) {
        // 这个xpathBoolean 是为了找到定义的boolean变量 = true
        String xpathBoolean = ".//FieldDeclaration/VariableDeclarator/VariableInitializer/Expression/PrimaryExpression"
                + "/PrimaryPrefix/Literal/BooleanLiteral[@True='true']";
        // 找出源码中所有以Log.*开头的代码
        pickUpLogMethods(node);

        if (astNamewithLog.isEmpty()) {
            return;
        }

        // 通过xpath获取所有定义的boolean类型的变量
        List<ASTBooleanLiteral> xpathBooleanStringNames = (List<ASTBooleanLiteral>) node.findChildNodesWithXPath(xpathBoolean);
        if (xpathBooleanStringNames.size() > 0) {
            for (ASTBooleanLiteral booleanLiteral : xpathBooleanStringNames) {
                // 从boolean型值的父节点中查找VariableDeclarator  比如： b = true;
                ASTVariableDeclarator variableDeclarator = booleanLiteral.getFirstParentOfType(ASTVariableDeclarator.class);
                // 这里是获取 变量的名称，比如 b
                ASTVariableDeclaratorId variableDeclaratorId = variableDeclarator.getFirstChildOfType(ASTVariableDeclaratorId.class);
                this.BooleanStrings.add(variableDeclaratorId.getImage());
            }
        }

        List<ASTName> xpathLogNames = this.astNamewithLog;
        for (ASTName name : xpathLogNames) {
            String imageString = name.getImage();
            // 这里重复判断一次，是否是包含Log.d的语句
            if (imageString != null && imageString.contains("Log.")) {
                // 检测Log.d是否被if语句包围
                ASTIfStatement ifStatement = name.getFirstParentOfType(ASTIfStatement.class);
                ASTBlockStatement blockStatement = name.getFirstParentOfType(ASTBlockStatement.class);
                List<ASTName> names2 = blockStatement.findDescendantsOfType(ASTName.class);
                if (names2.size() > 0) {
                    for (ASTName name2 : names2) {
                        if (name2 != null) {
                            String imageString2 = name2.getImage();
                            boolean sflag = CheckIsSensitiveString(imageString2);

                            // 没有发现包含敏感信息，把该ASTName节点存储后续解析
                            if (!sflag) {
                                this.SASTNames.add(name2);
                            }

                            // 当前发现包含敏感信息，确认是否被if包围
                            if (sflag) {
                                if (ifStatement != null) {

                                    // 这里是获取if语句中的boolean值，这里只判断了if（isTrue）的情况
                                    ASTExpression astExpression = ifStatement.getFirstDescendantOfType(ASTExpression.class);
                                    ASTName astName = astExpression.getFirstDescendantOfType(ASTName.class);
                                    if (astName != null) {
                                        String asstNameString = astName.getImage();
                                        if (this.BooleanStrings.size() > 0 && BooleanStrings.contains(asstNameString)) {
                                            // 这里从之前获取的所有Boolean变量为true中查找是否有当前的boolean值。如果有就记录当前的触发规则的数据
                                            addViolation(data, name2);
                                        }
                                    }
                                } else {
                                    // 没有被if包围，触发规则， 默认这里也是有规则判断的，建议都放到if（true）{}中
                                    addViolation(data, name2);
                                }
                            }
                        }
                    }
                }
            }
        }

        // 第二层敏感信息监测，这里是获取所有的变量值，比如b
        List<ASTVariableDeclaratorId> variableDeclaratorIds = node.findDescendantsOfType(ASTVariableDeclaratorId.class);
        // 找出定义的所有变量
        if (variableDeclaratorIds.size() > 0 ){
            for (ASTVariableDeclaratorId variableDeclaratorId : variableDeclaratorIds){
                // 获取变量的type类型节点
                ASTType type = variableDeclaratorId.getTypeNode();
                if (!(type.jjtGetParent() instanceof ASTFormalParameter)){
                    // 获取变量的值
                    ASTName astName=variableDeclaratorId.getFirstParentOfType(ASTVariableDeclarator.class).getFirstDescendantOfType(ASTName.class);
                    if(astName!=null){
                        if (CheckIsSensitiveString(astName.getImage())) {
                            this.SensitiveVariables.add(variableDeclaratorId);
                        }
                    }
                }
            }

            if (SensitiveVariables.size() > 0 ){

            }

        }
    }

    /**
     *  判断变量是否为null， 如果初始化为null，则剔除
     * @return
     */
    private boolean hasNullInitializer(ASTVariableDeclaratorId var){
        ASTVariableInitializer init = var.getFirstDescendantOfType(ASTVariableInitializer.class);
        if (init != null){
            List<?> nulls = init.findChildNodesWithXPath("Expression/PrimaryExpression/PrimaryPrefix/Literal/NullLiteral");
            return !nulls.isEmpty();
        }

        return false;
    }

    /**
     * 找出源代码中的log.*的代码
     */
    private void pickUpLogMethods(Node node){
        // 查找所有的语句表达式
        // 查找代码中的语句，比如：Log.d(Tag, msg);
        List<ASTStatementExpression> pexs = node.findDescendantsOfType(ASTStatementExpression.class);

        // 遍历所有的语句
        for (ASTStatementExpression ast : pexs){
            // 查找执行方法括号之前的部分
            // 这里是去获取Log.d
            ASTPrimaryPrefix primaryPrefix = ast.jjtGetChild(0).getFirstDescendantOfType(ASTPrimaryPrefix.class);

            if (primaryPrefix != null){
                // 获取name属性
                // 这里用到的是Log.d
                ASTName name = primaryPrefix.getFirstChildOfType(ASTName.class);
                if (name != null){
                    // 通过getImage来获取Log.d的字符串值"Log.d"
                    String imageString  = name.getImage();
                    if (imageString.startsWith("Log.")){
                        // 保存有Log.的ASTName
                        astNamewithLog.add(name);
                    }
                }
            }
        }
    }

    /**
     *  判断是否包含敏感信息
     *  @param imageString2
     * @return
     */
    private boolean CheckIsSensitiveString(String imageString2){
        if (imageString2 == null) return false;

        for (String sensitiveString : SensitiveStrings){
            if (imageString2.equalsIgnoreCase(sensitiveString)){
                return true;
            }

            // 处理类似Log.i("imei", imei.length);   Log.i("imei", imei.size()); 这种情况

            if (imageString2.contains(".")){
                String[] partStrings = imageString2.split("\\.");
                int LastIndex = partStrings.length - 1;
                if (partStrings[LastIndex].equals("length") || partStrings[LastIndex].equals("size")){
                    return false;
                }else {
                    for (int i = 0; i < partStrings.length; i++){
                        String partString = partStrings[i];
                        if (partString.equalsIgnoreCase(sensitiveString)){
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }
}
