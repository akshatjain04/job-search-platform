package io.myjobai.runtime;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.slf4j.*;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.*;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.*;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

@Configuration
public class SecurityConfiguration {
    @Bean SecurityFilterChain security(HttpSecurity http,IdentityService identity,PlatformSettings settings)throws Exception{
        http.csrf(c->c.disable()).cors(c->c.configurationSource(cors(settings))).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS)).requestCache(c->c.disable());
        http.authorizeHttpRequests(a->{a.requestMatchers("/actuator/health/**").permitAll();if(settings.role().equals("api"))a.requestMatchers("/api/v1/auth/config","/api/v1/auth/login","/api/v1/auth/callback","/api/v1/auth/test-login","/api/v1/auth/extension/token").permitAll().requestMatchers("/api/v1/**","/v3/api-docs/**").authenticated();if(settings.role().equals("mcp"))a.requestMatchers("/mcp").authenticated();a.anyRequest().denyAll();});
        http.exceptionHandling(e->e.authenticationEntryPoint((request,response,error)->failure(response,401,"UNAUTHENTICATED")).accessDeniedHandler((request,response,error)->failure(response,403,"FORBIDDEN")));
        http.headers(h->h.contentTypeOptions(c->{}).frameOptions(f->f.deny()).contentSecurityPolicy(c->c.policyDirectives("default-src 'none'; frame-ancestors 'none'")));
        http.addFilterBefore(new IdentityFilter(identity,settings),UsernamePasswordAuthenticationFilter.class);return http.build();
    }
    private CorsConfigurationSource cors(PlatformSettings settings){var c=new CorsConfiguration();var origins=new ArrayList<String>();origins.add(settings.publicUrl());for(String id:settings.get("EXTENSION_IDS","").split(","))if(id.matches("[a-p]{32}"))origins.add("chrome-extension://"+id);c.setAllowedOrigins(origins);c.setAllowCredentials(true);c.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"));c.setAllowedHeaders(List.of("Authorization","Content-Type","X-CSRF-Token","Idempotency-Key","MCP-Protocol-Version","Accept"));c.setExposedHeaders(List.of("X-Request-ID"));var source=new UrlBasedCorsConfigurationSource();source.registerCorsConfiguration("/**",c);return source;}
    static void failure(HttpServletResponse response,int status,String code)throws IOException{response.setStatus(status);response.setContentType("application/json");response.getWriter().write("{\"code\":\""+code+"\",\"message\":\"Request was not authorized\"}");}
    static String cookie(HttpServletRequest request,String name){if(request.getCookies()!=null)for(var cookie:request.getCookies())if(cookie.getName().equals(name))return cookie.getValue();return null;}
    static final class IdentityFilter extends OncePerRequestFilter {
        private final IdentityService identity;private final PlatformSettings settings;private final ConcurrentHashMap<String,Window> rate=new ConcurrentHashMap<>();
        private record Window(long minute,int count){}
        IdentityFilter(IdentityService identity,PlatformSettings settings){this.identity=identity;this.settings=settings;}
        protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
            String requestId=UUID.randomUUID().toString();response.setHeader("X-Request-ID",requestId);MDC.put("requestId",requestId);
            try{
                Optional<IdentityService.Principal> principal=Optional.empty();String auth=request.getHeader("Authorization");try{principal=auth!=null&&auth.startsWith("Bearer ")?identity.bearer(auth.substring(7)):identity.cookie(cookie(request,"myjobai-session"));}catch(RuntimeException e){failure(response,401,"SESSION_EXPIRED");return;}
                if(principal.isPresent()){
                    var p=principal.get();MDC.put("userId",p.id().toString());boolean safe=Set.of("GET","HEAD","OPTIONS").contains(request.getMethod());
                    if(!safe&&p.method().equals("cookie")&&!MessageDigest.isEqual(p.csrf().getBytes(StandardCharsets.UTF_8),Objects.toString(request.getHeader("X-CSRF-Token"),"").getBytes(StandardCharsets.UTF_8))){failure(response,403,"CSRF");return;}
                    long minute=System.currentTimeMillis()/60000;var window=rate.compute(p.id().toString(),(key,old)->old==null||old.minute()!=minute?new Window(minute,1):new Window(minute,old.count()+1));
                    if(rate.size()>10000)rate.entrySet().removeIf(e->e.getValue().minute()<minute);
                    if(window.count()>120){failure(response,429,"RATE_LIMIT");return;}
                    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p,null,List.of()));
                }
                chain.doFilter(request,response);
            }finally{MDC.clear();SecurityContextHolder.clearContext();}
        }
    }
}
