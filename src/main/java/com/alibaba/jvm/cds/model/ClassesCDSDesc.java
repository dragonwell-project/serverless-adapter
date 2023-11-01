package com.alibaba.jvm.cds.model;


import java.util.*;

public class ClassesCDSDesc {
    HashMap<String, ClassCDSDesc> idIds = new HashMap<>();
    Set<String> eagerCDSSet = new HashSet<>();
    Set<String> appCDSSet = new HashSet<>();
    Set<String> notFoundSet = new HashSet<>();

    List<ClassCDSDesc> all = new ArrayList<>();
    List<ClassCDSDesc> allNotFound = new ArrayList<>();
    int klassID = 1;

    public HashMap<String, ClassCDSDesc> getIdIds() {
        return idIds;
    }

    public int genKlassID() {
        return klassID++;
    }


    public void setIdIds(HashMap<String, ClassCDSDesc> idIds) {
        this.idIds = idIds;
    }

    public Set<String> getEagerCDSSet() {
        return eagerCDSSet;
    }

    public void setEagerCDSSet(Set<String> eagerCDSSet) {
        this.eagerCDSSet = eagerCDSSet;
    }

    public Set<String> getAppCDSSet() {
        return appCDSSet;
    }

    public void setAppCDSSet(Set<String> appCDSSet) {
        this.appCDSSet = appCDSSet;
    }

    public Set<String> getNotFoundSet() {
        return notFoundSet;
    }

    public void setNotFoundSet(Set<String> notFoundSet) {
        this.notFoundSet = notFoundSet;
    }

    public List<ClassCDSDesc> getAll() {
        return all;
    }

    public void setAll(List<ClassCDSDesc> all) {
        this.all = all;
    }

    public List<ClassCDSDesc> getAllNotFound() {
        return allNotFound;
    }

    public void setAllNotFound(List<ClassCDSDesc> allNotFound) {
        this.allNotFound = allNotFound;
    }
}
