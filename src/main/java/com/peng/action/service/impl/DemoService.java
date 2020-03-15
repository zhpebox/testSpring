package com.peng.action.service.impl;

import com.peng.action.service.IDemoService;
import com.peng.framework.annotation.ZHPService;

@ZHPService
public class DemoService implements IDemoService {

    @Override
    public String get(String name) {
        return "Hello "+name;
    }
}
