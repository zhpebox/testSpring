package com.peng.action.mvc;


import com.peng.action.service.IDemoService;
import com.peng.framework.annotation.ZHPAutowired;
import com.peng.framework.annotation.ZHPController;
import com.peng.framework.annotation.ZHPRequestMapping;
import com.peng.framework.annotation.ZHPRequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@ZHPController
@ZHPRequestMapping("/demo")
public class DemoAction {


    @ZHPAutowired
    private IDemoService demoService;

    @ZHPRequestMapping("/query")
    public void query(HttpServletRequest req, HttpServletResponse resp,
                      @ZHPRequestParam("name") String name){
        String result = demoService.get(name);
//		String result = "My name is " + name;
        try {
            resp.getWriter().write(result);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
