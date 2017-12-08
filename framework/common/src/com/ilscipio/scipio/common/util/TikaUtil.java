package com.ilscipio.scipio.common.util;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.lang.StringUtils;
import org.apache.tika.detect.Detector;
import org.apache.tika.detect.EncodingDetector;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MediaTypeRegistry;
import org.apache.tika.mime.MimeType;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.mime.MimeTypes;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.html.HtmlEncodingDetector;
import org.apache.tika.parser.txt.Icu4jEncodingDetector;
import org.apache.tika.parser.txt.UniversalEncodingDetector;
import org.ofbiz.base.util.Debug;
import org.ofbiz.base.util.UtilMisc;
import org.ofbiz.base.util.UtilValidate;
import org.ofbiz.entity.Delegator;
import org.ofbiz.entity.GenericEntityException;
import org.ofbiz.entity.GenericValue;

/**
 * Apache Tika file detection utility.
 * <p>
 * NOTE: distinction between entity MimeType and tika's MimeType class.
 * <p>
 * FIXME: a number of these methods may not handle mime types with parameters (;) properly.
 */
public abstract class TikaUtil {

    public static final String module = TikaUtil.class.getName();
    
    private TikaUtil() {
    }
    
    /* ******************************************************************** */
    /* Core Tika lookup and manipulation methods */
    /* ******************************************************************** */
    
    public static MediaTypeRegistry getMediaTypeRegistry() {
        return MediaTypeRegistry.getDefaultRegistry(); // TODO?: unhardcode registry selection;
    }
    
    public static MimeTypes getMimeTypeRegistry() {
        return MimeTypes.getDefaultMimeTypes(); // TODO?: unhardcode registry selection;
    }
    
    /**
     * Returns a mime type name/ID for MediaType as a standardized ID that can be used within Ofbiz,
     * such as for MimeType entity's mimeTypeId.
     * <p>
     * NOTE: this MUST be used instead of MediaType.toString() because it produces extra whitespace characters.
     * The whitespace is valid per RFC but makes things harder in ofbiz.
     * The tika-mimetypes.xml file doesn't use spaces either, so it's not always consistent.
     */
    public static String getMimeTypeId(MediaType mediaType) {
        return getMimeTypeId(mediaType.toString());
    }
    
    /**
     * Returns a mime type name/ID for the given mime-type as a standardized ID that can be used within Ofbiz,
     * such as for MimeType entity's mimeTypeId.
     */
    public static String getMimeTypeId(String mediaType) {
        // NOTE: names with semicolon-separated parameters receive extra spaces in toString; we need to strip
        return StringUtils.replace(mediaType, " ", "");
    }
    
    /**
     * Makes a non-normalized tika MediaType instance (non-normalized means it may be an alias).
     * Result NOT necessarily exists in the registry.
     */
    public static MediaType asMediaType(String mediaType) {
        return MediaType.parse(mediaType);
        // this code returned null for aliases.
        //MimeType mimeType = getMimeTypeForMediaTypeSafe(mediaType, getMimeTypeRegistry(), exact);
        //return mimeType != null ? mimeType.getType() : null;
    }
    
    /**
     * Makes a normalized tika MediaType instance (normalized means alias translated to the main recognized name).
     * Result NOT necessarily exists in the registry.
     */
    public static MediaType asNormMediaType(String mediaType) {
        return getMediaTypeRegistry().normalize(MediaType.parse(mediaType));
        // this code returned null for aliases.
        //MimeType mimeType = getMimeTypeForMediaTypeSafe(mediaType, getMimeTypeRegistry(), exact);
        //return mimeType != null ? mimeType.getType() : null;
    }
    
    /**
     * Finds media type (through Apache Tika library), based on filename and magic numbers.
     * @throws IOException 
     */
    public static MediaType findMediaType(ByteBuffer byteBuffer, String fileName) throws IOException {
        InputStream is = new ByteBufferInputStream(byteBuffer);
        try {
            return findMediaType(is, fileName);
        } finally {
            try {
                is.close();
            } catch (IOException e) {
                ;
            }
        }
    }
    
    /**
     * Finds media type (through Apache Tika library), based on filename and magic numbers.
     * @throws IOException
     */
    public static MediaType findMediaType(InputStream is, String fileName) throws IOException {
        BufferedInputStream bis = new BufferedInputStream(is);
        try {
            AutoDetectParser parser = new AutoDetectParser();
            Detector detector = parser.getDetector();
            Metadata md = new Metadata();
            md.add(Metadata.RESOURCE_NAME_KEY, fileName);
            MediaType mediaType = detector.detect(bis, md);
            return mediaType;
        } finally {
            try {
                bis.close();
            } catch (IOException e) {
                ;
            }
        }
    }
    
    public static MediaType findMediaTypeSafe(InputStream is, String fileName) {
        try {
            return findMediaType(is, fileName);
        } catch (IOException e) {
            return null;
        }
    }
    
    public static MediaType findMediaTypeSafe(ByteBuffer byteBuffer, String fileName) {
        try {
            return findMediaType(byteBuffer, fileName);
        } catch (IOException e) {
            return null;
        }
    }
    
    public static Set<MediaType> getMediaTypeAliases(MediaType mediaType) {
        return getMediaTypeAliases(mediaType, getMediaTypeRegistry());
    }
    
    /**
     * WARN: the registry.getAliases method does not work reliably. This method instead tries to work around
     * it so any alias can be passed, not just the canonical form... 
     * TODO: REVIEW: this may not work right for media types with parameters...
     */
    public static Set<MediaType> getMediaTypeAliases(MediaType mediaType, MediaTypeRegistry registry) {
        if (mediaType == null) {
            return Collections.<MediaType> emptySet();
        }
        MediaType normType = registry.normalize(mediaType);
        Set<MediaType> aliases = registry.getAliases(normType);
        if (aliases == null) {
            return Collections.<MediaType> emptySet();
        }
        if (normType.equals(mediaType)) {
            return aliases;
        } else {
            Set<MediaType> al = new TreeSet<>(aliases);
            al.remove(mediaType);
            al.add(normType);
            return al;
        }
    }
    
    public static Set<String> getMediaTypeAliasMimeTypeIds(MediaType mediaType) {
        Set<String> ids = new LinkedHashSet<>();
        for(MediaType alias : getMediaTypeAliases(mediaType)) {
            ids.add(getMimeTypeId(alias));
        }
        return ids;
    }
    
    public static Set<String> getMediaTypeAliasMimeTypeIds(String mediaType) {
        return getMediaTypeAliasMimeTypeIds(asMediaType(mediaType));
    }
    
    public static GenericValue findEntityMimeTypeForMediaType(Delegator delegator, MediaType mediaType, boolean checkAliases) throws GenericEntityException {
        if (mediaType == null) {
            return null;
        }
        GenericValue mimeType = delegator.findOne("MimeType", true, UtilMisc.toMap("mimeTypeId", getMimeTypeId(mediaType)));
        if (mimeType != null) {
            return mimeType;
        }
        if (checkAliases) {
            for(MediaType alias : getMediaTypeAliases(mediaType)) {
                mimeType = delegator.findOne("MimeType", true, UtilMisc.toMap("mimeTypeId", getMimeTypeId(alias)));
                if (mimeType != null) {
                    return mimeType;
                }
            }
        }
        return null;
    }
    
    /**
     * NOTE: this returns only normalized types, not aliases.
     */
    public static Set<MediaType> getAllMediaTypes() {
        return getMediaTypeRegistry().getTypes();
    }
    
    /**
     * Returns the top tika mime-type definition for the media type.
     * WARN: this only returns explicit defined mime-types (canonical), NOT aliases.
     * if exact true, will double-check that parameters match as well (not guaranteed by MimeTypes.getRegisteredMimeType).
     * FIXME: exact doesn't handle parameter order.
     */
    public static MimeType getMimeTypeForMediaTypeSafe(String mediaType, MimeTypes mimeTypes, boolean exact) {
        try {
            MimeType mimeType = mimeTypes.getRegisteredMimeType(mediaType);
            if (mimeType != null && exact) {
                // NOTE: because the way getRegisteredMimeType works, it may return non-null
                // even if not exact name match, due to parameters.
                // FIXME: this check won't handle parameter order difference
                // also check if another normalize call would be more appropriate...
                if (!getMimeTypeId(mediaType).equals(getMimeTypeId(mimeType.getName()))) {
                    return null;
                }
            }
            return mimeType;
        } catch (MimeTypeException e) {
            return null;
        }
    }
    
    /**
     * WARN: this only returns explicit defined mime-types (canonical), NOT aliases.
     * FIXME: exact doesn't handle parameter order.
     */
    public static MimeType getMimeTypeForMediaTypeSafe(MediaType mediaType, MimeTypes mimeTypes, boolean exact) {
        return getMimeTypeForMediaTypeSafe(mediaType.toString(), mimeTypes, exact);
    }
    
    /**
     * Finds charset (through Apache Tika library), based on filename, using default encoding detector class.
     * 
     * @param byteBuffer
     * @param fileName
     * @return
     * @throws IOException
     */
    public static Charset findCharset(ByteBuffer byteBuffer, String fileName) throws IOException {
        return findCharset(byteBuffer, fileName, UniversalEncodingDetector.class);
    }

    /**
     * Finds charset (through Apache Tika library), based on filename, using default encoding detector class.
     * 
     * @param is
     * @param fileName
     * @return
     * @throws IOException
     */
    public static Charset findCharset(InputStream is, String fileName) throws IOException {
        return findCharset(is, fileName, UniversalEncodingDetector.class);
    }

    /**
     * Finds charset (through Apache Tika library), based on filename.
     * @throws IOException 
     */
    public static Charset findCharset(ByteBuffer byteBuffer, String fileName, Class<? extends EncodingDetector> encodingDetectorClass) throws IOException {
        InputStream is = new ByteBufferInputStream(byteBuffer);
        try {
            return findCharset(is, fileName);
        } finally {
            try {
                is.close();
            } catch (IOException e) {
                ;
            }
        }
    }
    
    public static Charset findCharsetSafe(InputStream is, String fileName) {
        try {
            return findCharset(is, fileName);
        } catch (IOException e) {
            return null;
        }
    }
    
    public static Charset findCharsetSafe(ByteBuffer byteBuffer, String fileName) {
        try {
            return findCharset(byteBuffer, fileName);
        } catch (IOException e) {
            return null;
        }
    }
    
    /**
     * Finds charset (through Apache Tika library), based on filename.
     * @throws IOException
     */
    public static Charset findCharset(InputStream is, String fileName,  Class<? extends EncodingDetector> encodingDetectorClass) throws IOException  {
        if (encodingDetectorClass == null)
            return null;
        BufferedInputStream bis = new BufferedInputStream(is);
        try {
            EncodingDetector detector = encodingDetectorClass.newInstance();
            Metadata md = new Metadata();
            md.add(Metadata.RESOURCE_NAME_KEY, fileName);
            return detector.detect(is, md);
        } catch (InstantiationException e) {
            Debug.logError(e.getMessage(), module);
            return null;
        } catch (IllegalAccessException e) {
            Debug.logError(e.getMessage(), module);
            return null;
        } finally {
            try {
                bis.close();
            } catch (IOException e) {
                ;
            }
        }
    }
    
    
    /* ******************************************************************** */
    /* Meta-Information and XML Data Utilities  */
    /* ******************************************************************** */
    
    public static String getMediaTypeDescriptionAlwaysSafe(MediaType mediaType, MimeTypes mimeTypes, String overrideDesc, String defaultDesc) {
        if (UtilValidate.isNotEmpty(overrideDesc)) {
            return overrideDesc;
        }
        String description = getMediaTypeDescriptionOrNullSafe(mediaType, mimeTypes);
        if (UtilValidate.isNotEmpty(description)) {
            return description;
        } else {
            return defaultDesc;
        }
    }
    
    public static String getMediaTypeDescriptionOrNullSafe(MediaType mediaType, MimeTypes mimeTypes) {
        MimeType mimeType = getMimeTypeForMediaTypeSafe(mediaType, mimeTypes, true);
        String description = null;
        
        if (mimeType != null) {
            description = mimeType.getDescription();
        } else {
            // when this prints, it's because of imperfections in tika-mimetypes.xml...
            Debug.logWarning("No Tika mime-type for MediaType: " + mediaType.toString(), module);
        }
        if (UtilValidate.isNotEmpty(description)) {
            return description;
        } else {
            MediaTypeRegistry registry = mimeTypes.getMediaTypeRegistry();

            // check if can find one in supertype
            MediaType superType = registry.getSupertype(mediaType);
            if (superType != null) {
                description = getMediaTypeDescriptionOrNullSafe(superType, mimeTypes);
                if (UtilValidate.isNotEmpty(description)) {
                    return description + " (sub-type)";
                }
            }
        }
        return null;
    }
    
    public static List<GenericValue> getEntityMimeTypes(Delegator delegator) throws GenericEntityException {
        return delegator.findAll("MimeType", true);
    }
    
    /**
     * WARN: very slow, not for heavy use.
     */
    public static List<GenericValue> makeEntityMimeTypes(Delegator delegator, Collection<MediaType> mediaTypes, MimeTypes mimeTypes, boolean aliases, boolean missingOnly) throws GenericEntityException {
        List<GenericValue> mimeTypeValues = new ArrayList<>(mediaTypes.size());
        for(MediaType mediaType : mediaTypes) {
            GenericValue mainValue = makeEntityMimeType(delegator, mediaType, mimeTypes, "", getMimeTypeId(mediaType));
            if (!missingOnly || UtilValidate.isEmpty(delegator.findOne("MimeType", true, UtilMisc.toMap("mimeTypeId", getMimeTypeId(mediaType))))) {
                mimeTypeValues.add(mainValue);
            }
            if (aliases) {
                Set<MediaType> aliasSet = getMediaTypeAliases(mediaType, mimeTypes.getMediaTypeRegistry());
                for(MediaType alias : aliasSet) {
                    GenericValue aliasValue = makeEntityMimeType(delegator, alias, mimeTypes, 
                            mainValue.getString("description") + " (alias)", 
                            getMimeTypeId(alias) + " (alias for " + getMimeTypeId(mediaType) + ")");
                    if (!missingOnly || UtilValidate.isEmpty(delegator.findOne("MimeType", true, UtilMisc.toMap("mimeTypeId", getMimeTypeId(alias))))) {
                        mimeTypeValues.add(aliasValue);
                    }
                    
                    // SANITY CHECK
                    Set<MediaType> aliasAliasSet = getMediaTypeAliases(alias, mimeTypes.getMediaTypeRegistry());
                    if (aliasAliasSet.isEmpty()) {
                        Debug.logWarning("Tika: Sanity check failed: " + alias.toString() + " has no aliases (unlike its canonical form)", module);
                    } else if (aliasSet.size() != aliasAliasSet.size()) {
                        Debug.logWarning("Tika: Sanity check failed: " + alias.toString() + " has different number of aliases than its canonical form", module);
                    }
                }
            }
        }
        return mimeTypeValues;
    }
    
    public static GenericValue makeEntityMimeType(Delegator delegator, MediaType mediaType, MimeTypes mimeTypes, String overrideDesc, String defaultDesc) throws GenericEntityException {
        return delegator.makeValue("MimeType", 
                "mimeTypeId", getMimeTypeId(mediaType), 
                "description", getMediaTypeDescriptionAlwaysSafe(mediaType, mimeTypes, overrideDesc, defaultDesc));
    }
    
    /**
     * Makes a new GenericValue MimeType for each Tika MediaType.
     * <p>
     * Does not store.
     * WARN: UNIQUE NOT GUARANTEED
     */
    public static List<GenericValue> makeAllEntityMimeTypes(Delegator delegator, boolean aliases) throws GenericEntityException {
        MimeTypes mimeTypes = getMimeTypeRegistry();
        return makeEntityMimeTypes(delegator, mimeTypes.getMediaTypeRegistry().getTypes(), mimeTypes, aliases, false);
    }
    
    /**
     * Makes a new GenericValue MimeType for each Tika MediaType that does not have an entity MimeType in the system.
     * <p>
     * Does not store.
     * WARN: UNIQUE NOT GUARANTEED
     */
    public static List<GenericValue> makeMissingEntityMimeTypes(Delegator delegator, boolean aliases) throws GenericEntityException {
        MimeTypes mimeTypes = getMimeTypeRegistry();
        return makeEntityMimeTypes(delegator, mimeTypes.getMediaTypeRegistry().getTypes(), mimeTypes, aliases, true);
    }
    
    
    /* ******************************************************************** */
    /* Private Utilities  */
    /* ******************************************************************** */
        
    /**
     * FIXME: use something better
     */
    private static class ByteBufferInputStream extends InputStream {
        private ByteBuffer buf;
        
        public ByteBufferInputStream(ByteBuffer buf) {
            this.buf = buf;
        }
        
        public int read() throws IOException { // not needed: synchronized (keyword)
            if (!buf.hasRemaining()) {
                return -1;
            }
            return buf.get();
        }
        
        public int read(byte[] bytes, int off, int len) throws IOException { // not needed: synchronized (keyword)
            len = Math.min(len, buf.remaining());
            buf.get(bytes, off, len);
            return len;
        }
    }
    
}