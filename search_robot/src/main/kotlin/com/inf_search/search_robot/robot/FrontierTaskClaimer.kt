package com.inf_search.search_robot.robot

import com.inf_search.search_robot.entity.FrontierEntity
import com.inf_search.search_robot.entity.task.TaskKind
import com.inf_search.search_robot.entity.task.TaskStatus
import org.springframework.data.mongodb.core.FindAndModifyOptions
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class FrontierTaskClaimer(private val mongo: MongoTemplate) {
    fun claimNextPending(kind: TaskKind): FrontierEntity? {
        val now = Instant.now().epochSecond

        // Условие: PENDING + (время не задано ИЛИ время пришло)
        val query = Query(
            Criteria.where("status").`is`(TaskStatus.PENDING)
                .and("kind").`is`(kind)
                .orOperator(
                    Criteria.where("nextDueAt").exists(false),
                    Criteria.where("nextDueAt").`is`(null),
                    Criteria.where("nextDueAt").lte(now)
                )
        )

        val update = Update()
            .set("status", TaskStatus.IN_PROGRESS)
            .set("updatedAt", now)

        return mongo.findAndModify(
            query, update,
            FindAndModifyOptions.options().returnNew(true),
            FrontierEntity::class.java
        )
    }
}
