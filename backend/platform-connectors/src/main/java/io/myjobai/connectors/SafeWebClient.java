package io.myjobai.connectors;

import io.myjobai.application.IntegrationException;
import io.myjobai.domain.Checks;
import org.apache.hc.client5.http.*;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.*;
import org.apache.hc.client5.http.impl.classic.*;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.Timeout;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class SafeWebClient implements AutoCloseable {
    private final CloseableHttpClient client;
    public SafeWebClient(){
        var manager=PoolingHttpClientConnectionManagerBuilder.create().setMaxConnTotal(8).setMaxConnPerRoute(2).setDnsResolver(new DnsResolver(){
            public InetAddress[] resolve(String host)throws UnknownHostException{var addresses=InetAddress.getAllByName(host);for(var address:addresses)if(!isPublic(address))throw new UnknownHostException("Non-public destination is prohibited");return addresses;}
            public String resolveCanonicalHostname(String host){return host;}
        }).setDefaultConnectionConfig(ConnectionConfig.custom().setConnectTimeout(Timeout.ofSeconds(5)).setSocketTimeout(Timeout.ofSeconds(15)).build()).build();
        client=HttpClients.custom().setConnectionManager(manager).disableRedirectHandling().disableAutomaticRetries().setDefaultRequestConfig(RequestConfig.custom().setResponseTimeout(Timeout.ofSeconds(15)).setConnectionRequestTimeout(Timeout.ofSeconds(5)).build()).build();
    }
    public static boolean isPublic(InetAddress a){
        if(a.isAnyLocalAddress()||a.isLoopbackAddress()||a.isLinkLocalAddress()||a.isSiteLocalAddress()||a.isMulticastAddress())return false;
        byte[] b=a.getAddress();int first=b[0]&255;if(b.length==16)return (first&254)!=252&&!(first==32&&(b[1]&255)==1&&(b[2]&255)==13&&(b[3]&255)==184);
        int second=b[1]&255;return first!=0&&first<224&&!(first==100&&second>=64&&second<=127)&&!(first==192&&(second==0||second==2))&&!(first==198&&(second==18||second==19||second==51))&&!(first==203&&second==0);
    }
    public String get(String url){return get(url,Map.of());}
    public String get(String url,Map<String,String> headers){
        Checks.publicUrl(url);var request=new HttpGet(url);request.setHeader("User-Agent","MyJobAI/0.1 (+user-initiated research)");headers.forEach(request::setHeader);
        try{return client.execute(request,response->{int status=response.getCode();if(status<200||status>=300)throw new IntegrationException("SOURCE_HTTP","Source returned HTTP "+status,status==429||status>=500,false);try(var input=response.getEntity().getContent()){byte[] bytes=input.readNBytes(2000001);if(bytes.length>2000000)throw new IntegrationException("SOURCE_TOO_LARGE","Source exceeds 2 MB",false,false);return new String(bytes,StandardCharsets.UTF_8);}});}
        catch(IntegrationException e){throw e;}catch(Exception e){throw new IntegrationException("SOURCE_CONNECTION","Public source could not be fetched; redirects and non-public addresses are blocked",true,false);}
    }
    public void close()throws java.io.IOException{client.close();}
}
