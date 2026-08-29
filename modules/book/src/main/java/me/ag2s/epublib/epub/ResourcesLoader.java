package me.ag2s.epublib.epub;

import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

import me.ag2s.epublib.domain.EpubResourceProvider;
import me.ag2s.epublib.domain.LazyResource;
import me.ag2s.epublib.domain.LazyResourceProvider;
import me.ag2s.epublib.domain.MediaType;
import me.ag2s.epublib.domain.MediaTypes;
import me.ag2s.epublib.domain.Resource;
import me.ag2s.epublib.domain.Resources;
import me.ag2s.epublib.util.CollectionUtil;
import me.ag2s.epublib.util.ResourceUtil;
import me.ag2s.epublib.util.zip.ZipEntryWrapper;
import me.ag2s.epublib.util.zip.ZipFileWrapper;


/**
 * Loads Resources from inputStreams, ZipFiles, etc
 *
 * @author paul
 */
public class ResourcesLoader {

    private static final String TAG = ResourcesLoader.class.getName();


    /**
     * Loads the entries of the zipFileWrapper as resources.
     * <p>
     * The MediaTypes that are in the lazyLoadedTypes will not get their
     * contents loaded, but are stored as references to entries into the
     * AndroidZipFile and are loaded on demand by the Resource system.
     *
     * @param zipFileWrapper      import epub zipfile
     * @param defaultHtmlEncoding epub xhtml default encoding
     * @param lazyLoadedTypes     lazyLoadedTypes
     * @return Resources
     * @throws IOException IOException
     */
    public static Resources loadResources(
            ZipFileWrapper zipFileWrapper,
            String defaultHtmlEncoding,
            List<MediaType> lazyLoadedTypes
    ) throws IOException {

        LazyResourceProvider resourceProvider =
                new EpubResourceProvider(zipFileWrapper);

        Resources result = new Resources();
        Enumeration entries = zipFileWrapper.entries();

        while (entries.hasMoreElements()) {
            ZipEntryWrapper zipEntry = new ZipEntryWrapper(entries.nextElement());

            if (zipEntry == null || zipEntry.isDirectory()) {
                continue;
            }

            // Zip Slip 防御：消毒 zip entry name
            String safeName = sanitizeZipEntryName(zipEntry.getName());
            if (safeName == null) {
                Log.w(TAG, "Skip zip entry with suspicious name: " + zipEntry.getName());
                continue;
            }

            Resource resource;

            if (shouldLoadLazy(safeName, lazyLoadedTypes)) {
                resource = new LazyResource(resourceProvider, zipEntry.getSize(), safeName);
            } else {
                resource = ResourceUtil
                        .createResource(safeName, zipFileWrapper.getInputStream(zipEntry));
            }

            if (resource.getMediaType() == MediaTypes.XHTML) {
                resource.setInputEncoding(defaultHtmlEncoding);
            }
            result.add(resource);
        }

        return result;
    }

    /**
     * Whether the given href will load a mediaType that is in the
     * collection of lazilyLoadedMediaTypes.
     *
     * @param href                   href
     * @param lazilyLoadedMediaTypes lazilyLoadedMediaTypes
     * @return Whether the given href will load a mediaType that is
     * in the collection of lazilyLoadedMediaTypes.
     */
    private static boolean shouldLoadLazy(String href,
                                          Collection<MediaType> lazilyLoadedMediaTypes) {
        if (CollectionUtil.isEmpty(lazilyLoadedMediaTypes)) {
            return false;
        }
        MediaType mediaType = MediaTypes.determineMediaType(href);
        return lazilyLoadedMediaTypes.contains(mediaType);
    }

    /**
     * Loads all entries from the ZipInputStream as Resources.
     * <p>
     * Loads the contents of all ZipEntries into memory.
     * Is fast, but may lead to memory problems when reading large books
     * on devices with small amounts of memory.
     *
     * @param zipInputStream      zipInputStream
     * @param defaultHtmlEncoding defaultHtmlEncoding
     * @return Resources
     * @throws IOException IOException
     */
    public static Resources loadResources(ZipInputStream zipInputStream,
                                          String defaultHtmlEncoding) throws IOException {
        Resources result = new Resources();
        ZipEntry zipEntry;
        do {
            // get next valid zipEntry
            zipEntry = getNextZipEntry(zipInputStream);
            if ((zipEntry == null) || zipEntry.isDirectory()) {
                continue;
            }
            // Zip Slip 防御：消毒 zip entry name
            String safeName = sanitizeZipEntryName(zipEntry.getName());
            if (safeName == null) {
                Log.w(TAG, "Skip zip entry with suspicious name: " + zipEntry.getName());
                zipInputStream.closeEntry();
                continue;
            }

            // store resource
            Resource resource = ResourceUtil.createResource(safeName, zipInputStream);
            if (resource.getMediaType() == MediaTypes.XHTML) {
                resource.setInputEncoding(defaultHtmlEncoding);
            }
            result.add(resource);
        } while (zipEntry != null);

        return result;
    }


    private static ZipEntry getNextZipEntry(ZipInputStream zipInputStream)
            throws IOException {
        try {
            return zipInputStream.getNextEntry();
        } catch (ZipException e) {
            //see <a href="https://github.com/psiegman/epublib/issues/122">Issue #122 Infinite loop</a>.
            //when reading a file that is not a real zip archive or a zero length file, zipInputStream.getNextEntry()
            //throws an exception and does not advance, so loadResources enters an infinite loop
            //log.error("Invalid or damaged zip file.", e);
            Log.e(TAG, e.getLocalizedMessage());
            try {
                zipInputStream.closeEntry();
            } catch (Exception ignored) {
            }
            throw e;
        }
    }

    /**
     * Loads all entries from the ZipInputStream as Resources.
     * <p>
     * Loads the contents of all ZipEntries into memory.
     * Is fast, but may lead to memory problems when reading large books
     * on devices with small amounts of memory.
     *
     * @param zipFile             zipFile
     * @param defaultHtmlEncoding defaultHtmlEncoding
     * @return Resources
     * @throws IOException IOException
     */
    public static Resources loadResources(ZipFileWrapper zipFile, String defaultHtmlEncoding) throws IOException {
        List<MediaType> ls = new ArrayList<>();
        return loadResources(zipFile, defaultHtmlEncoding, ls);
    }

    /**
     * 消毒 zip entry name，防御 Zip Slip 攻击。
     * 拒绝包含路径穿越序列 ("..", "."), 绝对路径, 反斜杠 的名称。
     * 返回 null 表示名称无效。
     */
    static String sanitizeZipEntryName(String name) {
        if (name == null || name.isEmpty()) return null;
        // 拒绝绝对路径
        if (name.startsWith("/") || name.startsWith("\\")) return null;
        // 拒绝反斜杠（Windows 路径分隔符）
        if (name.contains("\\")) return null;
        // 统一斜杠
        String normalized = name.replace('\\', '/');
        // 拒绝包含 ".." 或 "." 段的条目
        for (String part : normalized.split("/")) {
            if (part.equals("..") || part.equals(".")) {
                return null;
            }
        }
        // 拒绝标准化后以 "/" 开头
        if (normalized.startsWith("/")) return null;
        return normalized;
    }
}
