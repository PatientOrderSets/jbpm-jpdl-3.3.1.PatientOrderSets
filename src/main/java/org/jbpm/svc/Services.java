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
package org.jbpm.svc;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.StaleStateException;
import org.jbpm.JbpmContext;
import org.jbpm.JbpmException;
import org.jbpm.graph.exe.ProcessInstance;
import org.jbpm.logging.LoggingService;
import org.jbpm.msg.MessageService;
import org.jbpm.persistence.JbpmPersistenceException;
import org.jbpm.persistence.PersistenceService;
import org.jbpm.persistence.db.StaleObjectLogConfigurer;
import org.jbpm.scheduler.SchedulerService;
import org.jbpm.security.AuthenticationService;
import org.jbpm.security.AuthorizationService;
import org.jbpm.svc.save.CascadeSaveOperation;
import org.jbpm.svc.save.CheckUnpersistableVariablesOperation;
import org.jbpm.svc.save.HibernateSaveOperation;
import org.jbpm.svc.save.SaveLogsOperation;
import org.jbpm.svc.save.SaveOperation;
import org.jbpm.tx.TxService;

public class Services implements Serializable
{
  private static final long serialVersionUID = 1L;

  public static final String SERVICENAME_AUTHENTICATION = "authentication";
  public static final String SERVICENAME_AUTHORIZATION = "authorization";
  public static final String SERVICENAME_TX = "tx";
  public static final String SERVICENAME_LOGGING = "logging";
  public static final String SERVICENAME_MESSAGE = "message";
  public static final String SERVICENAME_PERSISTENCE = "persistence";
  public static final String SERVICENAME_SCHEDULER = "scheduler";
  public static final String SERVICENAME_JCR = "jcr";
  public static final String SERVICENAME_ADDRESSRESOLVER = "addressresolver";

  static final List defaultSaveOperations = createDefaultSaveOperations();

  private static List createDefaultSaveOperations()
  {
    SaveOperation[] operations = new SaveOperation[4];
    operations[0] = new CheckUnpersistableVariablesOperation();
    // first we save the runtime data (process instance)
    operations[1] = new HibernateSaveOperation();
    // then we insert the logs cause the logs can have references
    // to the runtime data
    operations[2] = new SaveLogsOperation();
    operations[3] = new CascadeSaveOperation();
    return Arrays.asList(operations);
  }

  Map<String, ServiceFactory> serviceFactories;
  Map<String, Service> services;
  List serviceNames = null;
  List saveOperations = null;

  public static Service getCurrentService(String name)
  {
    return getCurrentService(name, true);
  }

  public static Service getCurrentService(String name, boolean isRequired)
  {
    Service service = null;
    JbpmContext jbpmContext = JbpmContext.getCurrentJbpmContext();
    if (jbpmContext != null)
    {
      service = jbpmContext.getServices().getService(name);
    }
    if (isRequired && (service == null))
    {
      throw new JbpmServiceException("service '" + name + "' unavailable");
    }
    return service;
  }

  public Services(Map serviceFactories)
  {
    this(serviceFactories, new ArrayList(serviceFactories.keySet()), null);
  }

  public Services(Map serviceFactories, List serviceNames, List saveOperations)
  {
    this.serviceFactories = serviceFactories;
    this.serviceNames = serviceNames;
    this.saveOperations = saveOperations != null ? saveOperations : defaultSaveOperations;
  }

  public void setSaveOperations(List saveOperations)
  {
    if (saveOperations == null)
    {
      throw new IllegalArgumentException("saveOperations cannot be null");
    }
    this.saveOperations = saveOperations;
  }

  public void addSaveOperation(SaveOperation saveOperation)
  {
    if (saveOperation == null)
    {
      throw new IllegalArgumentException("saveOperation cannot be null");
    }
    if (saveOperations == defaultSaveOperations)
    {
      saveOperations = new ArrayList(defaultSaveOperations);
    }
    saveOperations.add(saveOperation);
  }

  public Map<String, ServiceFactory> getServiceFactories()
  {
    if (serviceFactories == null)
    {
      serviceFactories = new HashMap<String, ServiceFactory>();
    }
    return serviceFactories;
  }

  public ServiceFactory getServiceFactory(String name)
  {
    return getServiceFactories().get(name);
  }

  public boolean hasService(String name)
  {
    boolean hasService = false;
    if (services != null)
    {
      hasService = services.containsKey(name);
    }
    return hasService;
  }

  public Service getService(String name)
  {
    if (services == null)
    {
      services = new HashMap<String, Service>();
    }
    Service service = services.get(name);
    if (service == null)
    {
      ServiceFactory serviceFactory = getServiceFactory(name);
      if (serviceFactory != null)
      {
        service = serviceFactory.openService();
        services.put(name, service);
      }
    }
    return service;
  }

  public void save(ProcessInstance processInstance, JbpmContext jbpmContext)
  {
    Iterator iter = saveOperations.iterator();

    if (log.isDebugEnabled())
    {
      if (saveOperations == defaultSaveOperations)
      {
        log.debug("executing default save operations");
      }
      else
      {
        log.debug("executing custom save operations");
      }
    }

    while (iter.hasNext())
    {
      SaveOperation saveOperation = (SaveOperation)iter.next();
      saveOperation.save(processInstance, jbpmContext);
    }
  }

  // services /////////////////////////////////////////////////////////////////

  public AuthenticationService getAuthenticationService()
  {
    return (AuthenticationService)getService(SERVICENAME_AUTHENTICATION);
  }

  public AuthorizationService getAuthorizationService()
  {
    return (AuthorizationService)getService(SERVICENAME_AUTHORIZATION);
  }

  public LoggingService getLoggingService()
  {
    return (LoggingService)getService(SERVICENAME_LOGGING);
  }

  public MessageService getMessageService()
  {
    return (MessageService)getService(SERVICENAME_MESSAGE);
  }

  public PersistenceService getPersistenceService()
  {
    return (PersistenceService)getService(SERVICENAME_PERSISTENCE);
  }

  public SchedulerService getSchedulerService()
  {
    return (SchedulerService)getService(SERVICENAME_SCHEDULER);
  }

  public TxService getTxService()
  {
    return (TxService)getService(SERVICENAME_TX);
  }

  public void setAuthenticationService(AuthenticationService authenticationService)
  {
    services.put(SERVICENAME_AUTHENTICATION, authenticationService);
  }

  public void setAuthorizationService(AuthorizationService authorizationService)
  {
    services.put(SERVICENAME_AUTHORIZATION, authorizationService);
  }

  public void setLoggingService(LoggingService loggingService)
  {
    services.put(SERVICENAME_LOGGING, loggingService);
  }

  public void setMessageService(MessageService messageService)
  {
    services.put(SERVICENAME_MESSAGE, messageService);
  }

  public void setPersistenceService(PersistenceService persistenceService)
  {
    services.put(SERVICENAME_PERSISTENCE, persistenceService);
  }

  public void setSchedulerService(SchedulerService schedulerService)
  {
    services.put(SERVICENAME_SCHEDULER, schedulerService);
  }

  public void setTxService(TxService txService)
  {
    services.put(SERVICENAME_TX, txService);
  }

  public void close()
  {
    if (services != null)
    {
      Exception firstException = null;
      Iterator iter = serviceNames.iterator();
      while (iter.hasNext())
      {
        String serviceName = (String)iter.next();
        Service service = (Service)services.get(serviceName);
        if (service != null)
        {
          try
          {
            log.debug("closing service '" + serviceName + "': " + service);
            service.close();
          }
          catch (JbpmPersistenceException e)
          {
            // if this is a stale state exception, the jbpm configuration has control over the logging
            if (isCausedByStaleState(e))
            {
              log.info("problem closing service '" + serviceName + "': optimistic locking failed");
              StaleObjectLogConfigurer.getStaleObjectExceptionsLog().error("problem closing service '" + serviceName + "': optimistic locking failed", e);
            }
            else
            {
              log.error("problem closing service '" + serviceName + "'", e);
            }
            if (firstException == null)
            {
              firstException = e;
            }
          }
          catch (Exception e)
          {
            // NOTE that Error's are not caught because that might halt the JVM and mask the original Error.
            log.error("problem closing service '" + serviceName + "'", e);
            if (firstException == null)
            {
              firstException = e;
            }
          }
        }
      }
      if (firstException != null)
      {
        if (firstException instanceof JbpmException)
        {
          throw (JbpmException)firstException;
        }
        throw new JbpmException("problem closing services", firstException);
      }
    }
  }

  public static boolean isCausedByStaleState(JbpmPersistenceException persistenceException)
  {
    for (Throwable cause = persistenceException.getCause(); cause != null; cause = cause.getCause())
    {
      if (cause instanceof StaleStateException)
        return true;
    }
    return false;
  }

  public static void assignId(Object object)
  {
    JbpmContext jbpmContext = JbpmContext.getCurrentJbpmContext();
    if (jbpmContext != null)
    {
      // give this process instance an id
      Services services = jbpmContext.getServices();
      if (services.hasService(Services.SERVICENAME_PERSISTENCE))
      {
        PersistenceService persistenceService = services.getPersistenceService();
        persistenceService.assignId(object);
      }
    }
  }

  private static Log log = LogFactory.getLog(Services.class);
}
