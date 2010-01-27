/*
 * File   : $Source: /alkacon/cvs/opencms/src/org/opencms/xml/containerpage/Attic/CmsConfigurationParser.java,v $
 * Date   : $Date: 2010/01/27 12:25:30 $
 * Version: $Revision: 1.6 $
 *
 * This library is part of OpenCms -
 * the Open Source Content Management System
 *
 * Copyright (C) 2002 - 2009 Alkacon Software (http://www.alkacon.com)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * For further information about Alkacon Software, please see the
 * company website: http://www.alkacon.com
 *
 * For further information about OpenCms, please see the
 * project website: http://www.opencms.org
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.opencms.xml.containerpage;

import org.opencms.file.CmsFile;
import org.opencms.file.CmsObject;
import org.opencms.file.CmsPropertyDefinition;
import org.opencms.file.CmsResource;
import org.opencms.file.CmsResourceFilter;
import org.opencms.file.types.CmsResourceTypeXmlContainerPage;
import org.opencms.i18n.CmsLocaleManager;
import org.opencms.main.CmsException;
import org.opencms.main.CmsIllegalStateException;
import org.opencms.main.OpenCms;
import org.opencms.util.CmsMacroResolver;
import org.opencms.util.PrintfFormat;
import org.opencms.workplace.CmsWorkplace;
import org.opencms.workplace.explorer.CmsExplorerTypeSettings;
import org.opencms.xml.CmsXmlUtils;
import org.opencms.xml.I_CmsXmlDocument;
import org.opencms.xml.content.CmsXmlContentFactory;
import org.opencms.xml.types.I_CmsXmlContentValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Class for managing the creation of new content elements in ADE.<p>
 * 
 * XML files in the VFS can be used to configure which files are used as
 * prototypes for new elements, and which file names are used for the new
 * elements.<p> 
 * 
 * @author Georg Westenberger
 * 
 * @version $Revision: 1.6 $ 
 * 
 * @since 7.6 
 */
public class CmsConfigurationParser {

    /** The format used for the macro replacement. */
    public static final String FILE_NUMBER_FORMAT = "%0.4d";

    /** The macro name for new file name patterns. */
    public static final String MACRO_NUMBER = "number";

    /** The tag name of the configuration for a single type. */
    public static final String N_ADE_TYPE = "ADEType";

    /** The tag name of the destination in the type configuration. */
    public static final String N_DESTINATION = "Destination";

    /** The tag name of the source file in the type configuration. */
    public static final String N_FOLDER = "Folder";

    /** The tag name of the source file in the type configuration. */
    public static final String N_PATTERN = "Pattern";

    /** The tag name of the source file in the type configuration. */
    public static final String N_SOURCE = "Source";

    /** Configuration data, read from xml content. */
    private Map<String, CmsConfigurationItem> m_configuration;

    /** New elements. */
    private List<CmsResource> m_newElements;

    /**
     * Constructs a new instance.<p>
     * 
     * @param cms the cms context used for reading the configuration
     * @param config the configuration file
     *  
     * @throws CmsException if something goes wrong
     */
    public CmsConfigurationParser(CmsObject cms, CmsResource config)
    throws CmsException {

        if (config.getTypeId() != CmsResourceTypeXmlContainerPage.CONFIGURATION_TYPE_ID) {
            throw new CmsIllegalStateException(Messages.get().container(
                Messages.ERR_CONFIG_WRONG_TYPE_2,
                CmsPropertyDefinition.PROPERTY_ADE_CNTPAGE_CONFIG,
                cms.getSitePath(config)));
        }

        m_configuration = new HashMap<String, CmsConfigurationItem>();
        m_newElements = new ArrayList<CmsResource>();

        CmsFile configFile = cms.readFile(config);
        I_CmsXmlDocument content = CmsXmlContentFactory.unmarshal(cms, configFile);
        parseConfiguration(cms, content);
    }

    /**
     * Returns a new file name for an element to be created based on a pattern.<p>
     * 
     * The pattern consists of a path which may contain the macro %(number), which 
     * will be replaced by the first 5-digit sequence for which the resulting file name is not already
     * used.<p>
     * 
     * Although this method is synchronized, it may still return a used file name in the unlikely
     * case that it is called after a previous call to this method, but before the resulting file name
     * was used to create a file.<p>  
     * 
     * This method was adapted from the method {@link org.opencms.file.collectors.A_CmsResourceCollector}<code>#getCreateInFolder</code>.<p>
     *
     * @param cms the CmsObject used for checking the existence of file names
     * @param pattern the pattern for new files
     * 
     * @return the new file name
     * 
     * @throws CmsException if something goes wrong
     */
    public String getNewFileName(CmsObject cms, String pattern) throws CmsException {

        // this method was adapted from A_CmsResourceCollector#getCreateInFolder
        pattern = cms.getRequestContext().removeSiteRoot(pattern);
        PrintfFormat format = new PrintfFormat(FILE_NUMBER_FORMAT);
        String folderName = CmsResource.getFolderPath(pattern);
        List<CmsResource> resources = cms.readResources(folderName, CmsResourceFilter.ALL, false);
        // now create a list of all resources that just contains the file names
        Set<String> result = new HashSet<String>();
        for (int i = 0; i < resources.size(); i++) {
            CmsResource resource = resources.get(i);
            result.add(cms.getSitePath(resource));
        }

        String checkFileName, checkTempFileName, number;
        CmsMacroResolver resolver = CmsMacroResolver.newInstance();
        int j = 0;
        do {
            number = format.sprintf(++j);
            resolver.addMacro(MACRO_NUMBER, number);
            // resolve macros in file name
            checkFileName = resolver.resolveMacros(pattern);
            // get name of the resolved temp file
            checkTempFileName = CmsWorkplace.getTemporaryFileName(checkFileName);
        } while (result.contains(checkFileName) || result.contains(checkTempFileName));
        return checkFileName;
    }

    /**
     * Helper method for retrieving the OpenCms type name for a given type id.<p>
     * 
     * @param typeId the id of the type
     * 
     * @return the name of the type
     * 
     * @throws CmsException if something goes wrong
     */
    protected String getTypeName(int typeId) throws CmsException {

        return OpenCms.getResourceManager().getResourceType(typeId).getTypeName();
    }

    /**
     * Returns the configuration as an unmodifiable map.<p>
     * 
     * @return the configuration as an unmodifiable map
     */
    public Map<String, CmsConfigurationItem> getConfiguration() {

        return Collections.unmodifiableMap(m_configuration);
    }

    /**
     * Returns the new elements.<p>
     * 
     * @return the new elements
     */
    public List<CmsResource> getNewElements() {

        return Collections.unmodifiableList(m_newElements);
    }

    /**
     * Parses a type configuration contained in an XML content.<p>
     * 
     * This method uses the first locale from the following list which has a corresponding
     * element in the XML content:
     * <ul>
     *  <li>the request context's locale</li>
     *  <li>the default locale</li>
     *  <li>the first locale available in the XML content</li>
     * </ul><p>
     *
     * @param cms the CmsObject to use for VFS operations
     * @param content the XML content with the type configuration
     * 
     * @throws CmsException if something goes wrong
     */
    public void parseConfiguration(CmsObject cms, I_CmsXmlDocument content) throws CmsException {

        Locale currentLocale = cms.getRequestContext().getLocale();
        Locale defaultLocale = CmsLocaleManager.getDefaultLocale();
        Locale locale = null;
        if (content.hasLocale(currentLocale)) {
            locale = currentLocale;
        } else if (content.hasLocale(defaultLocale)) {
            locale = defaultLocale;
        } else {
            List<Locale> locales = content.getLocales();
            if (locales.size() == 0) {
                throw new CmsException(Messages.get().container(
                    Messages.ERR_NO_TYPE_CONFIG_1,
                    content.getFile().getRootPath()));
            }
            locale = locales.get(0);
        }

        Iterator<I_CmsXmlContentValue> itTypes = content.getValues(N_ADE_TYPE, locale).iterator();
        while (itTypes.hasNext()) {
            I_CmsXmlContentValue xmlType = itTypes.next();
            String typePath = xmlType.getPath();
            String source = content.getValue(CmsXmlUtils.concatXpath(typePath, N_SOURCE), locale).getStringValue(cms);
            String folder = content.getValue(
                CmsXmlUtils.concatXpath(typePath, CmsXmlUtils.concatXpath(N_DESTINATION, N_FOLDER)),
                locale).getStringValue(cms);
            String pattern = content.getValue(
                CmsXmlUtils.concatXpath(typePath, CmsXmlUtils.concatXpath(N_DESTINATION, N_PATTERN)),
                locale).getStringValue(cms);
            CmsConfigurationItem configItem = new CmsConfigurationItem(source, folder, pattern);
            CmsResource resource = cms.readResource(source);
            String type = getTypeName(resource.getTypeId());
            m_configuration.put(type, configItem);

            // checking access entries for the explorer-type
            CmsResource folderRes = cms.readResource(folder);
            CmsExplorerTypeSettings settings = OpenCms.getWorkplaceManager().getExplorerTypeSetting(
                OpenCms.getResourceManager().getResourceType(resource).getTypeName());
            boolean editable = settings.isEditable(cms, folderRes);
            //TODO: fix wrong permission test for explorer-types
            boolean controlPermission = settings.getAccess().getPermissions(cms, folderRes).requiresControlPermission();
            if (editable && controlPermission) {
                m_newElements.add(resource);
            }
        }
    }
}
