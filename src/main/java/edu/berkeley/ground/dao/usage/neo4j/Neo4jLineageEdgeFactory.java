/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package edu.berkeley.ground.dao.usage.neo4j;

import edu.berkeley.ground.dao.usage.LineageEdgeFactory;
import edu.berkeley.ground.dao.versions.neo4j.Neo4jItemFactory;
import edu.berkeley.ground.db.DbDataContainer;
import edu.berkeley.ground.db.Neo4jClient;
import edu.berkeley.ground.exceptions.EmptyResultException;
import edu.berkeley.ground.exceptions.GroundDbException;
import edu.berkeley.ground.exceptions.GroundException;
import edu.berkeley.ground.model.models.Tag;
import edu.berkeley.ground.model.usage.LineageEdge;
import edu.berkeley.ground.model.versions.GroundType;
import edu.berkeley.ground.util.IdGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.neo4j.driver.v1.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Neo4jLineageEdgeFactory extends LineageEdgeFactory {
  private static final Logger LOGGER = LoggerFactory.getLogger(Neo4jLineageEdgeFactory.class);
  private final Neo4jClient dbClient;
  private final Neo4jItemFactory itemFactory;

  private final IdGenerator idGenerator;

  /**
   * Constructor for the Neo4j lineage edge factory.
   *
   * @param itemFactory the singleton Neo4jItemFactory
   * @param dbClient the Neo4j client
   * @param idGenerator a unique id generator
   */
  public Neo4jLineageEdgeFactory(Neo4jItemFactory itemFactory,
                                 Neo4jClient dbClient, IdGenerator idGenerator) {
    this.dbClient = dbClient;
    this.itemFactory = itemFactory;
    this.idGenerator = idGenerator;
  }

  /**
   * Create and persist a lineage edge.
   *
   * @param name the name of the lineage edge
   * @param sourceKey the user generated unique id of the lineage edge
   * @param tags the tags associated with this lineage edge
   * @return the created lineage edge
   * @throws GroundException an unexpected error while creating or persisting this lineage edge
   */
  @Override
  public LineageEdge create(String name, String sourceKey, Map<String, Tag> tags)
      throws GroundException {
    try {
      long uniqueId = this.idGenerator.generateItemId();


      List<DbDataContainer> insertions = new ArrayList<>();
      insertions.add(new DbDataContainer("name", GroundType.STRING, name));
      insertions.add(new DbDataContainer("id", GroundType.LONG, uniqueId));
      insertions.add(new DbDataContainer("source_key", GroundType.STRING, sourceKey));

      this.dbClient.addVertex("LineageEdges", insertions);
      this.itemFactory.insertIntoDatabase(uniqueId, tags);

      this.dbClient.commit();
      LOGGER.info("Created lineage edge " + name + ".");

      return LineageEdgeFactory.construct(uniqueId, name, sourceKey, tags);
    } catch (GroundDbException e) {
      this.dbClient.abort();

      throw e;
    }
  }

  /**
   * Retrieve a lineage edge from the database.
   *
   * @param name the name of the lineage edge
   * @return the retrieved lineage edge
   * @throws GroundException either the lineage edge doesn't exist or couldn't be retrieved
   */
  @Override
  public LineageEdge retrieveFromDatabase(String name) throws GroundException {
    try {
      List<DbDataContainer> predicates = new ArrayList<>();
      predicates.add(new DbDataContainer("name", GroundType.STRING, name));

      Record record;
      try {
        record = this.dbClient.getVertex(predicates);
      } catch (EmptyResultException e) {
        throw new GroundDbException("No LineageEdge found with name " + name + ".");
      }

      long id = record.get("v").asNode().get("id").asLong();
      String sourceKey = record.get("v").asNode().get("source_key").asString();

      Map<String, Tag> tags = this.itemFactory.retrieveFromDatabase(id).getTags();

      this.dbClient.commit();
      LOGGER.info("Retrieved lineage edge " + name + ".");

      return LineageEdgeFactory.construct(id, name, sourceKey, tags);
    } catch (GroundDbException e) {
      this.dbClient.abort();

      throw e;
    }
  }

  @Override
  public void update(long itemId, long childId, List<Long> parentIds) throws GroundException {
    this.itemFactory.update(itemId, childId, parentIds);
  }

  @Override
  public void truncate(long itemId, int numLevels) throws GroundException {
    this.itemFactory.truncate(itemId, numLevels, "lineageEdge");
  }
}
