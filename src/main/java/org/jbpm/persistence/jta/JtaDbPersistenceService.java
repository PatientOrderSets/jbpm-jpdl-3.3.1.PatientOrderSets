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
package org.jbpm.persistence.jta;

import javax.transaction.SystemException;
import javax.transaction.UserTransaction;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.engine.SessionFactoryImplementor;
import org.hibernate.util.JTAHelper;
import org.jbpm.JbpmException;
import org.jbpm.persistence.db.DbPersistenceService;

public class JtaDbPersistenceService extends DbPersistenceService {

  private static final long serialVersionUID = 1L;

  private static Log log = LogFactory.getLog(JtaDbPersistenceService.class);

  private UserTransaction userTransaction;

  public JtaDbPersistenceService(JtaDbPersistenceServiceFactory persistenceServiceFactory) {
    super(persistenceServiceFactory);

    if (!isJtaTransactionInProgress()) {
      beginUserTransaction();
    }
  }

  public boolean isTransactionActive() {
    return isJtaTransactionInProgress();
  }

  protected boolean isTransactionExternallyManaged() {
    return !isJtaTxCreated();
  }

  public void close() {
    super.close();

    if (userTransaction != null) {
      endUserTransaction();
    }
  }

  boolean isJtaTransactionInProgress() {
    SessionFactoryImplementor sessionFactory = (SessionFactoryImplementor) getSessionFactory();
    return JTAHelper.isTransactionInProgress(sessionFactory);
  }

  void beginUserTransaction() {
    try {
      log.debug("begin user transaction");
      userTransaction = ((JtaDbPersistenceServiceFactory) persistenceServiceFactory)
          .getUserTransaction();
      userTransaction.begin();
    } catch (Exception e) {
      throw new JbpmException("couldn't begin user transaction", e);
    }
  }

  void endUserTransaction() {
    if (isRollbackOnly() || JTAHelper.isRollback(getUserTransactionStatus())) {
      log.debug("rolling back user transaction");
      try {
        userTransaction.rollback();
      } catch (Exception e) {
        throw new JbpmException("couldn't rollback user transaction", e);
      }
    } else {
      log.debug("committing user transaction");
      try {
        userTransaction.commit();
      } catch (Exception e) {
        throw new JbpmException("couldn't commit user transaction", e);
      }
    }
  }

  int getUserTransactionStatus() {
    try {
      return userTransaction.getStatus();
    } catch (SystemException e) {
      throw new JbpmException("couldn't get status for user transaction", e);
    }
  }

  public boolean isJtaTxCreated() {
    return userTransaction != null;
  }
}
