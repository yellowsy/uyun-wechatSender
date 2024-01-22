package uyun.bird.notify;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.taobao.api.ApiException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import uyun.bird.notify.common.ServiceManager;
import uyun.bird.notify.common.utils.ConfigUtils;
import uyun.bird.notify.common.utils.JsonUtil;
import uyun.tenant.serviceapi.UserServiceApi;
import uyun.tenant.serviceapi.dto.UserDTO;

import java.net.URI;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;


/**
 * @Author: hsy
 * @Description: 企业微信消息管理
 * @Date: 2024/1/15 17:03
 */
public class CorporateWechatManager {
    private static final Logger log = LoggerFactory.getLogger(CorporateWechatManager.class);

    private static final String configCorpId = "notify.wechat.corpid";

    private static final String configCorpSecret = "notify.wechat.corpsecret";

    private static final String configAgentId = "notify.wechat.agentId";

    private static final String configBaseUrl= "notify.wechat.baseUrl";

    private static final String configMobileUrl = "notify.wechat.mobileUrl";


    private static String corpId;

    private static String corpSecret;

    private static String agentId;

    private static String baserUrl;

    private static String mobileUrl;

    static {
        corpId = ConfigUtils.getValue(configCorpId);
        corpSecret = ConfigUtils.getValue(configCorpSecret);
        agentId = ConfigUtils.getValue(configAgentId);
        baserUrl = ConfigUtils.getValue(configBaseUrl);
        mobileUrl = ConfigUtils.getValue(configMobileUrl);

        log.info("corpId={}", corpId);
        log.info("corpSecret={}", corpSecret);
        log.info("agentId={}", agentId);
        log.info("baserUrl={}", baserUrl);
        log.info("mobileUrl={}", mobileUrl);
    }

    private static final RestTemplate restTemplate = MyRestTemplate.createRestTemplate();

    public static String getAccessToken(){
        String url = baserUrl + "/wechat/gettoken?corpid=" + corpId + "&corpsecret=" + corpSecret;
        log.info("getTokenUrl:{}",url);
        ResponseEntity<String> response  = restTemplate.getForEntity(url, String.class);
        JSONObject json = JSON.parseObject(response.getBody());
        log.info("access_token={}", JsonUtil.encode(json.getString("access_token")));
        return json.getString("access_token");
    }

    private static String getUserIdByMobile(String mobile){
        String url = baserUrl + "/wechat/user/getuserid?access_token=" + getAccessToken();
        log.info("根据电话获取用户id的Url:{}",url);
        //请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        //请求体参数
        JSONObject requestBody = new JSONObject();
        requestBody.put("mobile",mobile);
        //发送请求
        HttpEntity<Object> requestEntity = new HttpEntity<>(requestBody, headers);
        ResponseEntity<String> responseEntity = restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);
        JSONObject json = JSON.parseObject(responseEntity.getBody());
        log.info("微信用户userid={}", json.getString("userid"));
        return json.getString("userid");
    }

    public static void sendWechatMsg(String jsonMessage, List<String> userIds) throws ApiException{
        log.info("userIds={}", JsonUtil.encode(userIds));
        if(CollectionUtils.isEmpty(userIds)){
            throw new ApiException("no receivers");
        }
        String url = baserUrl + "/wechat/message/send?access_token=" + getAccessToken();
        log.info("推送卡片Url:{}",url);
        //要发送的微信用户
        List<String> wechatIds = convertUserToWechatIds(userIds);
        String touser = StringUtils.join(wechatIds.toArray(), "|");
        log.info("touser={}", JsonUtil.encode(touser));
        //获取消息内容中的工单ID
        JSONObject jsonMsg = JSON.parseObject(jsonMessage);
        log.info("jsonMsg={}",jsonMsg);
        String ticketId = jsonMsg.getString("ticketId");
        if(StringUtils.isEmpty(ticketId)){
            throw new ApiException("no ticket_id");
        }
        //工单流水号
        String ticketFlowNo = jsonMsg.getString("ticketFlowNo");
        //工单标题
        String ticketTitle = jsonMsg.getString("ticketTitle");
        //拼接移动端跳转地址
        String mobileLink = getMobileLink(ticketId);
        //请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        //请求体参数
        HashMap<String, Object> messageParams = setMessageParams(touser, mobileLink,ticketFlowNo,ticketTitle);
        String requestBody = JSON.toJSONString(messageParams);
        //发送请求
        HttpEntity<Object> requestEntity = new HttpEntity<>(requestBody, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(url, requestEntity, String.class);
        log.info("发送推送卡片请求返回结果：{}",response);
    }

    //根据传入的用户id查询微信用户id
    private static List<String> convertUserToWechatIds(List<String> userIds){
        UserServiceApi userServiceApi = ServiceManager.getInstance().getUserServiceApi();
        userIds = userIds.stream().distinct().collect(Collectors.toList());
        log.info("优云用户id:{}",userIds);
        List<UserDTO> users = userServiceApi.listByUserIds(userIds);
        log.info("优云用户:{}",users);
        LinkedList<String> resList = new LinkedList<>();
        for (UserDTO userDTO : users){
            if(userIds.contains(userDTO.getUserId())){
                if(StringUtils.isNotBlank(userDTO.getMobile())){
                    log.info("优云用户电话:{}",userDTO.getMobile());
                    String userId = getUserIdByMobile(userDTO.getMobile());
                    resList.add(userId);
                }
            }
        }
        return resList;
    }

    private static String getMobileLink(String ticketId){
        StringBuilder link = new StringBuilder();
        if (StringUtils.isNotBlank(ticketId)) {
            StringBuilder url = new StringBuilder();
            url.append(mobileUrl).append(ticketId).append("?");

            String encodedStr = "";
            try {
                encodedStr = URLEncoder.encode(url.toString(), "UTF-8");
            } catch (Exception e) {
                log.error("url转码失败:{}", e);
            }
            log.info("encodedStr:{}",encodedStr);
            log.info("agentid:{}",agentId);
            link.append("https://open.weixin.qq.com/connect/oauth2/authorize?")
                    .append("appid=").append(corpId)
                    .append("&redirect_uri=").append(encodedStr)
                    .append("&agentid=").append(agentId)
                    .append("&response_type=code&scope=snsapi_privateinfo&state=STATE#wechat_redirect");
        }

        log.info("移动端完整跳转链接：{}", link);
        return link.toString();
    }

    //构造发送的消息参数
    private static HashMap<String, Object> setMessageParams(String touser, String mobileLink,String ticketFlowNo,String ticketTitle){
        HashMap<String, Object> messageParams  = new HashMap<>();
        messageParams.put("touser", touser);
        messageParams.put("msgtype", "textcard");
        messageParams.put("topatry","");
        messageParams.put("totag","");
        messageParams.put("agentid", agentId);

        HashMap<String, Object> textCardParams = new HashMap<>();
        textCardParams.put("title","ITSM代办工单提醒");
        textCardParams.put("description","<div class=\"highlight\">工单流水号："+ticketFlowNo+"</div> <div class=\"highlight\">工单标题："+ticketTitle+"</div>");
        textCardParams.put("url",mobileLink);
        textCardParams.put("btntxt","点击处理");

        messageParams.put("textcard",textCardParams);
        messageParams.put("enable_id_trans",0);
        messageParams.put("enable_duplicate_check",0);
        messageParams.put("duplicate_check_interval",1800);

        log.info("消息参数：{}",JSON.toJSONString(messageParams));
        return messageParams;
    }
}
