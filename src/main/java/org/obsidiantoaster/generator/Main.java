/**
 *  Copyright 2005-2015 Red Hat, Inc.
 *
 *  Red Hat licenses this file to you under the Apache License, version
 *  2.0 (the "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 *  implied.  See the License for the specific language governing
 *  permissions and limitations under the License.
 */
package org.obsidiantoaster.generator;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.wildfly.swarm.Swarm;
import org.wildfly.swarm.config.infinispan.CacheContainer;
import org.wildfly.swarm.config.infinispan.Mode;
import org.wildfly.swarm.config.infinispan.cache_container.FileStore;
import org.wildfly.swarm.config.infinispan.cache_container.JGroupsTransport;
import org.wildfly.swarm.config.infinispan.cache_container.LockingComponent;
import org.wildfly.swarm.config.infinispan.cache_container.LockingComponent.Isolation;
import org.wildfly.swarm.config.infinispan.cache_container.TransactionComponent;
import org.wildfly.swarm.infinispan.InfinispanFraction;
import org.wildfly.swarm.management.ManagementFraction;
import org.wildfly.swarm.undertow.UndertowFraction;

/**
 *
 * @author <a href="mailto:ggastald@redhat.com">George Gastaldi</a>
 */
public class Main
{
   public static void main(String[] args) throws Exception
   {
      // Skip unnecessary build status checks in Forge
      System.setProperty("PROJECT_BUILDSTATUS_SKIP", "true");
      Swarm swarm = new Swarm();
      Path keyStorePath = keystorePath();
      System.out.println("Keystore created in " + keyStorePath);
      // Avoid enabling management port
      swarm.fraction(new ManagementFraction());
      swarm.fraction(UndertowFraction.createDefaultFraction(keyStorePath.toString(), "password", "appserver"));

      //TODO Default caching needs tweaking
      CacheContainer webCache = new CacheContainer("web")
              .defaultCache("dist")
              .jgroupsTransport(new JGroupsTransport().lockTimeout(60000L))
              .distributedCache("dist", distCache -> distCache
                      .mode(Mode.ASYNC)
                      .l1Lifespan(0L)
                      .owners(2)
                      .lockingComponent(new LockingComponent().isolation(Isolation.REPEATABLE_READ))
                      .transactionComponent(new TransactionComponent().mode(TransactionComponent.Mode.BATCH))
                      .fileStore(new FileStore()));

      InfinispanFraction infinispanFraction = new InfinispanFraction();
      infinispanFraction.cacheContainer(webCache);
      swarm.fraction(infinispanFraction);
      swarm.start().deploy();
   }

   private static Path keystorePath() throws IOException
   {
      // Copy keystore to tmp file
      Path tmpFile = Files.createTempFile("keystore", ".jks");
      try (InputStream is = Main.class.getClassLoader().getResourceAsStream("keystore.jks"))
      {
         ByteArrayOutputStream baos = new ByteArrayOutputStream();
         byte[] b = new byte[4096];
         int i = -1;
         while ((i = is.read(b)) != -1)
         {
            baos.write(b, 0, i);
         }
         Files.write(tmpFile, baos.toByteArray());
      }
      return tmpFile;
   }
}
