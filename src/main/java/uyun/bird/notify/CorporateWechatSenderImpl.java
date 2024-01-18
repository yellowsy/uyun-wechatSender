package uyun.bird.notify;

import com.alibaba.fastjson.JSONObject;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uyun.bird.notify.api.*;
import uyun.bird.notify.common.utils.JsonUtil;

import java.util.Arrays;


/**
 * @Author: hsy
 * @Description: 企业微信发送消息
 * @Date: 2024/1/15 16:28
 */
public class CorporateWechatSenderImpl implements NotifyServiceCommonSender {
    private static final Logger log = LoggerFactory.getLogger(CorporateWechatSenderImpl.class);

    @Override
    public SendResult sendMessage(MessageEntity messageParams) throws NotifyException {
        log.info("messageParams={}", JsonUtil.encode(messageParams));
        SendResult sendResult = new SendResult();
        sendResult.setBizId(messageParams.getBizId());
        try {
            String jsonMessage;
            if (messageParams.getExtend() == null) {
                jsonMessage = messageJsonFormat(messageParams.getMessage());
            } else {
                jsonMessage = messageParams.getExtend();
            }
            log.info("jsonMessage={}", jsonMessage);

            if (CollectionUtils.isNotEmpty(messageParams.getUserIds())) {
                messageParams.setReceivers(messageParams.getUserIds());
            }
            CorporateWechatManager.sendWechatMsg(jsonMessage, messageParams.getReceivers());
            sendResult.setSuccess(true);
        } catch (Exception e) {
            sendResult.setSuccess(false);
            String message = e.getMessage();
            if (StringUtils.isNotBlank(message) && message.length() > 500)
                message = message.substring(0, 500);
            sendResult.setErrorMessage(message);
        }
        return sendResult;
    }

    @Override
    public SendResult sendEmail(EmailEntity emailParams) throws NotifyException {
        return null;
    }

    //将消息内容中的传递的工单信息转为json格式
    private String messageJsonFormat(String message){
        // 将字符串按行分割
        String[] lines = message.split("\\n");
        // 创建一个JSON对象
        JSONObject jsonObject = new JSONObject();
        // 解析每行并添加到JSON对象中
        for (String line : lines) {
            String[] keyValue = line.split(":");
            if (keyValue.length >= 2) {
                String key = keyValue[0].trim();
                String value = String.join(":", Arrays.copyOfRange(keyValue, 1, keyValue.length)).trim();
                jsonObject.put(key, value);
            }
        }
        //格式化后的JSON字符串
        return jsonObject.toJSONString();
    }
}
