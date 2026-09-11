package io.myjobai.storage;

import io.myjobai.domain.DomainException;
import java.util.UUID;

final class StorageKeys {
    private StorageKeys(){}
    static String owned(UUID user,String key){if(key==null||!key.startsWith(user+"/")||!key.matches("[A-Za-z0-9/_.,-]+")||key.contains("..")||key.contains("//"))throw DomainException.missing();return key;}
    static String create(UUID user,String key){return owned(user,user+"/"+key);}
}
