package neon.app.http

import io.circe.Encoder
import neon.app.auth.{AuthDirectives, AuthenticationService}
import neon.common.{Permission, TaskId}
import neon.core.{AsyncTaskLifecycleService, TaskLifecycleError}
import neon.task.{AsyncTaskRepository, Task}
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.Route

import java.time.Instant
import java.util.UUID
import scala.concurrent.ExecutionContext

import CirceSupport.given

/** Mobile-oriented read endpoints for the Task aggregate. Companion to [[TaskRoutes]] (writes);
  * kept separate because the read shape is stable across both the supervisor dashboard and the
  * picker app, while writes have per-action request types. Lives under `/tasks` and is registered
  * alongside [[TaskRoutes]] in [[HttpServer.routes]].
  */
object MobileTaskRoutes:

  private case class TaskView(
      id: String,
      taskType: String,
      state: String,
      skuId: String,
      orderId: String,
      waveId: Option[String],
      handlingUnitId: Option[String],
      requestedQuantity: Int,
      actualQuantity: Option[Int],
      sourceLocationId: Option[String],
      destinationLocationId: Option[String],
      assignedTo: Option[String]
  ) derives Encoder.AsObject

  private case class TaskListResponse(tasks: List[TaskView]) derives Encoder.AsObject

  private case class ClaimTaskResponse(
      status: String,
      taskId: String,
      userId: String
  ) derives Encoder.AsObject

  private def toView(task: Task): TaskView =
    task match
      case t: Task.Planned =>
        TaskView(
          id = t.id.value.toString,
          taskType = t.taskType.toString,
          state = "Planned",
          skuId = t.skuId.value.toString,
          orderId = t.orderId.value.toString,
          waveId = t.waveId.map(_.value.toString),
          handlingUnitId = t.handlingUnitId.map(_.value.toString),
          requestedQuantity = t.requestedQuantity,
          actualQuantity = None,
          sourceLocationId = None,
          destinationLocationId = None,
          assignedTo = None
        )
      case t: Task.Allocated =>
        TaskView(
          id = t.id.value.toString,
          taskType = t.taskType.toString,
          state = "Allocated",
          skuId = t.skuId.value.toString,
          orderId = t.orderId.value.toString,
          waveId = t.waveId.map(_.value.toString),
          handlingUnitId = t.handlingUnitId.map(_.value.toString),
          requestedQuantity = t.requestedQuantity,
          actualQuantity = None,
          sourceLocationId = Some(t.sourceLocationId.value.toString),
          destinationLocationId = Some(t.destinationLocationId.value.toString),
          assignedTo = None
        )
      case t: Task.Assigned =>
        TaskView(
          id = t.id.value.toString,
          taskType = t.taskType.toString,
          state = "Assigned",
          skuId = t.skuId.value.toString,
          orderId = t.orderId.value.toString,
          waveId = t.waveId.map(_.value.toString),
          handlingUnitId = t.handlingUnitId.map(_.value.toString),
          requestedQuantity = t.requestedQuantity,
          actualQuantity = None,
          sourceLocationId = Some(t.sourceLocationId.value.toString),
          destinationLocationId = Some(t.destinationLocationId.value.toString),
          assignedTo = Some(t.assignedTo.value.toString)
        )
      case t: Task.Completed =>
        TaskView(
          id = t.id.value.toString,
          taskType = t.taskType.toString,
          state = "Completed",
          skuId = t.skuId.value.toString,
          orderId = t.orderId.value.toString,
          waveId = t.waveId.map(_.value.toString),
          handlingUnitId = t.handlingUnitId.map(_.value.toString),
          requestedQuantity = t.requestedQuantity,
          actualQuantity = Some(t.actualQuantity),
          sourceLocationId = Some(t.sourceLocationId.value.toString),
          destinationLocationId = Some(t.destinationLocationId.value.toString),
          assignedTo = Some(t.assignedTo.value.toString)
        )
      case t: Task.Cancelled =>
        // Task.Cancelled drops requestedQuantity in the aggregate (it's a
        // terminal state); emit 0 here so the JSON shape stays stable.
        TaskView(
          id = t.id.value.toString,
          taskType = t.taskType.toString,
          state = "Cancelled",
          skuId = t.skuId.value.toString,
          orderId = t.orderId.value.toString,
          waveId = t.waveId.map(_.value.toString),
          handlingUnitId = t.handlingUnitId.map(_.value.toString),
          requestedQuantity = 0,
          actualQuantity = None,
          sourceLocationId = t.sourceLocationId.map(_.value.toString),
          destinationLocationId = t.destinationLocationId.map(_.value.toString),
          assignedTo = t.assignedTo.map(_.value.toString)
        )

  def apply(
      taskRepository: AsyncTaskRepository,
      taskLifecycleService: AsyncTaskLifecycleService,
      authService: AuthenticationService
  )(using ExecutionContext): Route =
    pathPrefix("tasks"):
      concat(
        // GET /tasks?assignedTo=<userId>&state=<state>
        //
        // Lists tasks for an operator. Without assignedTo, returns the current
        // user's tasks (a convenience for the mobile picker). The optional
        // state filter narrows to active vs historical work.
        pathEnd:
          get:
            AuthDirectives.requirePermission(
              Permission.TaskComplete,
              authService
            ): context =>
              parameters(
                "assignedTo".as[String].optional,
                "state".as[String].optional
              ): (assignedToParam, stateParam) =>
                val targetUser = assignedToParam
                  .map(s => neon.common.UserId(UUID.fromString(s)))
                  .getOrElse(context.userId)
                onSuccess(
                  taskRepository.findAssignedTo(targetUser, stateParam)
                ): tasks =>
                  complete(TaskListResponse(tasks.map(toView)))
        ,
        // GET /tasks/{id}
        AuthDirectives.requirePermission(
          Permission.TaskComplete,
          authService
        ): _ =>
          path(Segment): taskIdStr =>
            get:
              val taskId = TaskId(UUID.fromString(taskIdStr))
              onSuccess(taskRepository.findById(taskId)):
                case Some(task) => complete(toView(task))
                case None       => complete(StatusCodes.NotFound)
        ,
        // POST /tasks/{id}/claim — operator self-assign
        AuthDirectives.requirePermission(
          Permission.TaskAssign,
          authService
        ): context =>
          path(Segment / "claim"): taskIdStr =>
            post:
              val taskId = TaskId(UUID.fromString(taskIdStr))
              onSuccess(
                taskLifecycleService.assign(
                  taskId,
                  context.userId,
                  Instant.now()
                )
              ):
                case Right(result) =>
                  complete(
                    ClaimTaskResponse(
                      status = "assigned",
                      taskId = result.assigned.id.value.toString,
                      userId = result.assigned.assignedTo.value.toString
                    )
                  )
                case Left(error) => mapLifecycleError(error)
      )

  private def mapLifecycleError(
      error: TaskLifecycleError
  ): Route =
    error match
      case _: TaskLifecycleError.TaskNotFound =>
        complete(StatusCodes.NotFound)
      case _: TaskLifecycleError.TaskInWrongState =>
        complete(StatusCodes.Conflict)
      case _: TaskLifecycleError.TaskAlreadyTerminal =>
        complete(StatusCodes.Conflict)
      case _: TaskLifecycleError.UserNotFound =>
        complete(StatusCodes.UnprocessableEntity)
      case _: TaskLifecycleError.UserNotActive =>
        complete(StatusCodes.UnprocessableEntity)
