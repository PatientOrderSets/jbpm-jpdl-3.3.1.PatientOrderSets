/*
 * JBoss, Home of Professional Open Source
 * Copyright 2005, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jbpm.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.jbpm.JbpmConfiguration;
import org.jbpm.JbpmException;
import org.jbpm.graph.def.ProcessDefinition;
import org.jbpm.instantiation.ProcessClassLoader;
import org.jbpm.instantiation.ProcessClassLoaderFactory;

/**
 * provides centralized classloader lookup. 
 */
public class ClassLoaderUtil
{

  private ClassLoaderUtil()
  {
    // hide default constructor to prevent instantiation
  }

  public static Class loadClass(String className)
  {
    try
    {
      return getClassLoader().loadClass(className);
    }
    catch (ClassNotFoundException e)
    {
      throw new JbpmException("class not found '" + className + "'", e);
    }
  }

  /*
   * returns the {@link ClassLoader} which is used in jbpm. Can be configured in jbpm.cfg.xml by the property <b>'jbpm.classloader'</b> <td> <li>'jbpm': (default value)
   * uses the {@link ClassLoaderUtil}.class.getClassLoader() {@link ClassLoader}. This was the only behavior available before <a
   * href="https://jira.jboss.org/jira/browse/JBPM-1148">JBPM-1148</a>.</li> <li>'context': uses the Thread.currentThread().getContextClassLoader().</li> <li>'custom':
   * means that a ClassLoader class has to be provided in the property <b>'jbpm.classloader.classname'</b></li> </td>
   */
  public static ClassLoader getClassLoader()
  {
    if (JbpmConfiguration.Configs.hasObject("jbpm.classLoader"))
    {
      String jbpmClassloader = JbpmConfiguration.Configs.getString("jbpm.classLoader");

      if (jbpmClassloader.equals("jbpm"))
      {
        return ClassLoaderUtil.class.getClassLoader();
      }
      else if (jbpmClassloader.equals("context"))
      {
        return Thread.currentThread().getContextClassLoader();
      }
      else if (jbpmClassloader.equals("custom"))
      {
        String jbpmClassloaderClassname = null;
        try
        {
          if (!JbpmConfiguration.Configs.hasObject("jbpm.customClassLoader.className"))
          {
            throw new JbpmException("'jbpm.classloader' property set to 'custom' but 'jbpm.customClassLoader.className' is empty!");
          }
          jbpmClassloaderClassname = JbpmConfiguration.Configs.getString("jbpm.customClassLoader.className");
          if (jbpmClassloaderClassname == null)
          {
            throw new JbpmException("'jbpm.classloader' property set to 'custom' but 'jbpm.customClassLoader.className' is empty!");
          }

          Class clazz = ClassLoaderUtil.class.getClassLoader().loadClass(jbpmClassloaderClassname);
          if (clazz == null)
            clazz = Thread.currentThread().getContextClassLoader().loadClass(jbpmClassloaderClassname);

          return (ClassLoader)clazz.newInstance();
        }
        catch (InstantiationException e)
        {
          throw new JbpmException("Error instantiating custom classloader " + jbpmClassloaderClassname, e);
        }
        catch (IllegalAccessException e)
        {
          throw new JbpmException("Error accessing custom classloader " + jbpmClassloaderClassname, e);
        }
        catch (ClassNotFoundException e)
        {
          throw new JbpmException("Custom classloader " + jbpmClassloaderClassname + " not found ", e);
        }
      }
      else
      {
        throw new JbpmException("'jbpm.classloader' property set to '" + jbpmClassloader + "' but only the values 'jbpm'/'context'/'custom' are supported!");
      }
    }
    else
    {
      // default behavior like before https://jira.jboss.org/jira/browse/JBPM-1148
      return ClassLoaderUtil.class.getClassLoader();
    }
  }

  public static InputStream getStream(String resource)
  {
    return getClassLoader().getResourceAsStream(resource);
  }

  /*
   * Load jbpm configuration related resources as stream (normally jbpm.cfg.xml). This method first tries to load the resource from the {@link ClassLoaderUtil} class
   * loader, if not found it tries the context class loader. If this doesn't return any ressource the call is delegated to the class loader configured by calling
   * getClassLoader(). This is a special method because the class loader which has to be used for loading the jbpm.cfg.xml cannot be configured in the jbpm.cfg.xml
   * itself.
   */
  public static InputStream getJbpmConfigurationStream(String resource)
  {
    InputStream jbpmCfgStream = ClassLoaderUtil.class.getClassLoader().getResourceAsStream(resource);
    if (jbpmCfgStream == null)
    {
      jbpmCfgStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource);
    }
    return jbpmCfgStream;
  }

  public static Properties getProperties(String resource)
  {
    Properties properties = new Properties();
    try
    {
      InputStream inStream = getStream(resource);
      properties.load(inStream);
      inStream.close();
    }
    catch (IOException e)
    {
      throw new JbpmException("couldn't load properties file '" + resource + "'", e);
    }
    return properties;
  }
}
