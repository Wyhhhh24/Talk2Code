package com.air.aicodemaster.service.impl;

import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.air.aicodemaster.model.entity.App;
import com.air.aicodemaster.mapper.AppMapper;
import com.air.aicodemaster.service.AppService;
import org.springframework.stereotype.Service;

/**
 * 应用 服务层实现。
 *
 * @author Wyhhhh
 */
@Service
public class AppServiceImpl extends ServiceImpl<AppMapper, App>  implements AppService{

}
