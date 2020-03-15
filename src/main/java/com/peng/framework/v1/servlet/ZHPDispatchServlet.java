package com.peng.framework.v1.servlet;

import com.peng.framework.annotation.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;

public class ZHPDispatchServlet extends HttpServlet {

    //appliction的配置内容
    private Properties prop = new Properties();

    private List<String> classNames = new ArrayList<String>();

    //IOC容器，保存所有实例化对象
    //注册式单例模式
    private Map<String,Object> ioc = new HashMap<String,Object>();

    private Map<String,Method> handlerMapping = new HashMap<String, Method>();


    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            this.doDispatch(req, resp);
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws IOException, InvocationTargetException, IllegalAccessException {
        String url = req.getRequestURI();
        String contextPath = req.getContextPath();
        url = url.replaceAll(contextPath,"").replaceAll("/+","/");
        if(!this.handlerMapping.containsKey(url)){
            resp.getWriter().write("404 Not Found!!");
            return;
        }

        Method method = this.handlerMapping.get(url);
        //第一个参数：方法所在的实例
        //第二个参数：调用时所需要的实参
//        Map<String,String[]> params = req.getParameterMap();
        //获取方法的形参列表
        Class<?> [] parameterTypes = method.getParameterTypes();
        //保存请求的url参数列表
        Map<String,String[]> parameterMap = req.getParameterMap();
        //保存赋值参数的位置
        Object [] paramValues = new Object[parameterTypes.length];
        //按根据参数位置动态赋值
        for (int i = 0; i < parameterTypes.length; i ++){
            Class parameterType = parameterTypes[i];
            if(parameterType == HttpServletRequest.class){
                paramValues[i] = req;
                continue;
            }else if(parameterType == HttpServletResponse.class){
                paramValues[i] = resp;
                continue;
            }else if(parameterType == String.class){

                //提取方法中加了注解的参数
                Annotation[] [] pa = method.getParameterAnnotations();
                for (int j = 0; j < pa.length ; j ++) {
                    for(Annotation a : pa[i]){
                        if(a instanceof ZHPRequestParam){
                            String paramName = ((ZHPRequestParam) a).value();
                            if(!"".equals(paramName.trim())){
                                String value = Arrays.toString(parameterMap.get(paramName))
                                        .replaceAll("\\[|\\]","")
                                        .replaceAll("\\s",",");
                                paramValues[i] = value;
                            }
                        }
                    }
                }

            }
        }
        //投机取巧的方式
        //通过反射拿到method所在class，拿到class之后还是拿到class的名称
        //再调用toLowerFirstCase获得beanName
        String beanName = toLowerFirstCase(method.getDeclaringClass().getSimpleName());
        method.invoke(ioc.get(beanName),new Object[]{req,resp,parameterMap.get("name")[0]});
    }

    @Override
    public void init(ServletConfig config) throws ServletException {

            //模板模式

        System.out.println(config);

            //1、加载配置文件
            doLoadConfig(config.getInitParameter("contextC"));
            //2、扫描相关的类
            doScanner(prop.getProperty("scanPackage"));
            //3、初始化所有相关的类的实例，并且放入到IOC容器之中
            doInstance();
            //4、完成依赖注入
            doAutowired();
            //5、初始化HandlerMapping
            initHandlerMapping();

            System.out.println("GP Spring framework is init.");
    }

    private void initHandlerMapping() {
        if (ioc.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            Class clazz = entry.getValue().getClass();

            if (!clazz.isAnnotationPresent(ZHPController.class)) {
                continue;
            }

            String baseUrl = "";

            //获取controller的配置
            if (clazz.isAnnotationPresent(ZHPRequestMapping.class)) {
                ZHPRequestMapping requestMapping = entry.getValue().getClass().getAnnotation(ZHPRequestMapping.class);
                baseUrl = requestMapping.value();
            }

            //获取方法的配置
            Method[] methods = clazz.getMethods();
            for (Method method : methods) {
                if (!method.isAnnotationPresent(ZHPRequestMapping.class)) {
                    continue;
                }

                ZHPRequestMapping methodRequest = method.getAnnotation(ZHPRequestMapping.class);
                String url = ("/" + baseUrl + "/" + methodRequest.value()).replaceAll("/+", "/");
                handlerMapping.put(url, method);

                System.out.println("Mapped " + url + "," + method);
            }
        }
        System.out.println(handlerMapping);

    }

    private void doAutowired() {
        if(ioc.isEmpty()){return;}
        for(Map.Entry<String,Object> entry : ioc.entrySet()){

            Field[] fields = entry.getValue().getClass().getDeclaredFields();
            for (Field field : fields) {
                if(field.isAnnotationPresent(ZHPAutowired.class)){
                    ZHPAutowired autowired = field.getAnnotation(ZHPAutowired.class);
                    String beanName = autowired.value().trim();
                    if("".equals(beanName)){
                        beanName = field.getType().getName();
                    }
                    field.setAccessible(true);

                    try {
                        field.set(entry.getValue(),ioc.get(beanName));
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                        continue;
                    }
                }
            }
        }
        System.out.println(ioc);
    }

    private void doInstance() {
        if(classNames.isEmpty()){
            return;
        }

        try {
            for(String className : classNames){
                Class<?> clazz = Class.forName(className);


                if(clazz.isAnnotationPresent(ZHPController.class)){
                    Object instance = clazz.newInstance();
                    String beanName = toLowerFirstCase(clazz.getSimpleName());
                    ioc.put(beanName,instance);
                }else if(clazz.isAnnotationPresent(ZHPService.class)){
                    String beanName = toLowerFirstCase(clazz.getSimpleName());

                    ZHPService service = clazz.getAnnotation(ZHPService.class);
                    if(!"".equals(service.value())){
                        beanName = service.value();
                    }
                    Object instance = clazz.newInstance();
                    ioc.put(beanName,instance);

                    for(Class<?> i : clazz.getInterfaces()){
                        if(ioc.containsKey(i.getName())){
                            throw new Exception("The beanName is exists!!");
                        }
                        ioc.put(i.getName(),instance);
                    }
                }else{
                    continue;
                }

            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void doScanner(String scanPackage) {

        URL url = this.getClass().getClassLoader()
                .getResource("./"+scanPackage.replaceAll("\\.","/"));
        File classPath = new File(url.getFile());

        for(File e : classPath.listFiles()){
            if(e.isDirectory()){
                doScanner(scanPackage+"."+e.getName());
            } else {
                if(!e.getName().endsWith(".class")){
                    continue;
                }
                String className = (scanPackage+"."+e.getName()).replace(".class","");
                classNames.add(className);
            }
        }

    }

    private void doLoadConfig(String contextC) {
        InputStream in = null;
        try {
            in = this.getClass().getClassLoader().getResourceAsStream(contextC);
            prop.load(in);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if(null!=in){
                try {
                    in.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private String toLowerFirstCase(String simpleName) {
        char [] chars = simpleName.toCharArray();
        chars[0] += 32;
        return  String.valueOf(chars);
    }
}
