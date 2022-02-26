package com.sap.fontus.gdpr.openolat;

import com.sap.fontus.config.Configuration;
import com.sap.fontus.config.Source;
import com.sap.fontus.gdpr.Utils;
import com.sap.fontus.gdpr.metadata.*;
import com.sap.fontus.gdpr.metadata.simple.SimpleDataId;
import com.sap.fontus.gdpr.metadata.simple.SimpleDataSubject;
import com.sap.fontus.gdpr.metadata.simple.SimpleGdprMetadata;
import com.sap.fontus.gdpr.servlet.ReflectedHttpServletRequest;
import com.sap.fontus.gdpr.servlet.ReflectedSession;
import com.sap.fontus.taintaware.IASTaintAware;
import com.sap.fontus.taintaware.shared.IASBasicMetadata;
import com.sap.fontus.taintaware.shared.IASTaintMetadata;
import com.sap.fontus.taintaware.shared.IASTaintSource;
import com.sap.fontus.taintaware.shared.IASTaintSourceRegistry;
import com.sap.fontus.taintaware.unified.IASString;
import com.sap.fontus.taintaware.unified.IASTaintHandler;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;

public class OpenOlatTaintHandler extends IASTaintHandler {

    /**
     * Sets Taint Information in OpenOLAT according to request information.
     *
     * @param taintAware The Taint Aware String-like object
     * @param parent     The object on which this method is being called
     * @param parameters The parameters used to make the method call
     * @param sourceId   The ID of the source function (internal)
     * @return A possibly tainted version of the input object
     */
    private static IASTaintAware setFormTaint(IASTaintAware taintAware, Object parent, Object[] parameters, int sourceId) {
        IASTaintHandler.printObjectInfo(taintAware, parent, parameters, sourceId);
        assert (parameters.length == 4);
        try {
            Object ureq = parameters[2];
            Object sr = Utils.invokeGetter(ureq, "getHttpReq");
            ReflectedHttpServletRequest request = new ReflectedHttpServletRequest(sr);
            //System.out.printf("Servlet Request: %s%n", request.toString());
            ReflectedSession rs = request.getSession();
            long userId = getSessionUserId(rs);
            DataSubject ds = new SimpleDataSubject(String.valueOf(userId));
            GdprMetadata metadata = new SimpleGdprMetadata(
                    Utils.getPurposesFromRequest(request),
                    ProtectionLevel.Normal,
                    ds,
                    new SimpleDataId(),
                    true,
                    true,
                    Identifiability.NotExplicit);
            taintAware.setTaint(new GdprTaintMetadata(sourceId, metadata));
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return taintAware;
    }

    private static IASTaintAware setTaint(IASTaintAware taintAware, Object parent, Object[] parameters, int sourceId) {
        // General debug info
        IASTaintHandler.printObjectInfo(taintAware, parent, parameters, sourceId);
        IASTaintSource taintSource = IASTaintSourceRegistry.getInstance().get(sourceId);
        Source source = Configuration.getConfiguration().getSourceConfig().getSourceWithName(taintSource.getName());
        IASTaintMetadata metaData = getBasicTaintMetaDataFromRequest(parent, sourceId);
        taintAware.setTaint(metaData);
        return taintAware;
    }

    private static IASTaintMetadata getBasicTaintMetaDataFromRequest(Object requestObject, int sourceId) {
        IASTaintSource taintSource = IASTaintSourceRegistry.getInstance().get(sourceId);
        ReflectedHttpServletRequest request = new ReflectedHttpServletRequest(requestObject);
        ReflectedSession session = request.getSession();
        long userId = getSessionUserId(session);
        // if userId == -1 -> not logged in
        if (userId == -1L) {
            return new IASBasicMetadata(taintSource);
        }
        DataSubject ds = new SimpleDataSubject(String.valueOf(userId));
        GdprMetadata metadata = new SimpleGdprMetadata(
                Utils.getPurposesFromRequest(request),
                ProtectionLevel.Normal,
                ds,
                new SimpleDataId(),
                true,
                true,
                Identifiability.NotExplicit);
        return new GdprTaintMetadata(sourceId, metadata);
    }

    /**
     * The taint method can be used as a taintHandler for a given taint source
     *
     * @param object   The object to be tainted
     * @param sourceId The ID of the taint source function
     * @return The tainted object
     * <p>
     * This snippet of XML can be added to the source:
     *
     * <tainthandler>
     * <opcode>184</opcode>
     * <owner>com/sap/fontus/gdpr/openolat/OpenOlatTaintHandler</owner>
     * <name>taint</name>
     * <descriptor>(Ljava/lang/Object;Ljava/lang/Object;[Ljava/lang/Object;I)Ljava/lang/Object;</descriptor>
     * <interface>false</interface>
     * </tainthandler>
     */
    public static Object taint(Object object, Object parent, Object[] parameters, int sourceId) {
        if (object instanceof IASTaintAware) {
            return setTaint((IASTaintAware) object, parent, parameters, sourceId);
        }
        return IASTaintHandler.traverseObject(object, taintAware -> setTaint(taintAware, parent, parameters, sourceId));
    }

    public static Object formTaint(Object object, Object parent, Object[] parameters, int sourceId) {
        if (object instanceof IASTaintAware) {
            return setFormTaint((IASTaintAware) object, parent, parameters, sourceId);
        }
        return IASTaintHandler.traverseObject(object, taintAware -> setFormTaint(taintAware, parent, parameters, sourceId));
    }

    public static Object contactTracingTaint(Object object, Object parent, Object[] parameters, int sourceId) throws InvocationTargetException, IllegalAccessException, NoSuchMethodException {
        IASTaintAware taintAware = (IASTaintAware) object;
        if (!taintAware.isTainted()) {
            System.err.printf("The string '%s' should be tainted but it isn't!!!!%n", taintAware);
            Object identity = Utils.invokeGetter(parent, "getIdentity", 2);
            long userId = (long) Utils.invokeGetter(identity, "getKey");
            DataSubject ds = new SimpleDataSubject(String.valueOf(userId));
            GdprMetadata metadata = new SimpleGdprMetadata(
                    new ArrayList<>(),
                    ProtectionLevel.Normal,
                    ds,
                    new SimpleDataId(),
                    true,
                    true,
                    Identifiability.NotExplicit);
            taintAware.setTaint(new GdprTaintMetadata(sourceId, metadata));
        }
        // TODO: Adjust expiry date accordingly
        boolean adjusted = Utils.updateExpiryDatesAndProtectionLevel(taintAware, 14L, ProtectionLevel.Sensitive);
        System.out.printf("Adjusted the expiry date/protection level for String '%s' successfully: %b%n", taintAware, adjusted);
        return object;
    }

    private static Long getSessionUserId(ReflectedSession session) {
        try {
            Object us = session.getAttribute(new IASString("org.olat.core.util.UserSession"));
            Object si = Utils.invokeGetter(us, "getSessionInfo");
            if (si == null) {
                return -1L;
            }
            Long identityKey = (Long) Utils.invokeGetter(si, "getIdentityKey");
            return identityKey;
        } catch (Exception ex) {
            //ex.printStackTrace();
            return -1L;
        }
    }
}
