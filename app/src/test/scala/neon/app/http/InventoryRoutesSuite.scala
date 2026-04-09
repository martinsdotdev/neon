package neon.app.http

import neon.app.auth.*
import neon.common.{InventoryId, LocationId, Lot, PackagingLevel, Permission, Role, SkuId, UserId}
import neon.core.{
  AsyncInventoryService,
  InventoryCreateResult,
  InventoryError,
  InventoryMutationResult
}
import neon.inventory.Inventory
import neon.user.User
import io.circe.Json
import io.circe.parser.parse
import org.apache.pekko.http.scaladsl.model.headers.Cookie
import org.apache.pekko.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.funspec.AnyFunSpec

import java.time.Instant
import scala.concurrent.duration.*
import scala.concurrent.{Await, Future}

class InventoryRoutesSuite extends AnyFunSpec with ScalatestRouteTest:

  private val inventoryId = InventoryId()
  private val locationId = LocationId()
  private val skuId = SkuId()
  private val userId = UserId()
  private val at = Instant.now()

  private val hasher = PasswordHasher()
  private val testUser = User(
    id = userId,
    login = "operator",
    name = "Test Operator",
    role = Role.Admin,
    passwordHash = Some(hasher.hash("password")),
    active = true
  )

  private val authService = AuthenticationService(
    InMemoryAsyncUserRepository(testUser),
    InMemorySessionRepository(),
    InMemoryPermissionRepository(
      Map(Role.Admin -> Permission.values.toSet)
    ),
    hasher
  )

  private val sessionToken: String = Await
    .result(
      authService.login("operator", "password", None, None),
      5.seconds
    )
    .toOption
    .get
    ._1

  private def stubService(
      createResult: Either[
        InventoryError,
        InventoryCreateResult
      ],
      reserveResult: Either[
        InventoryError,
        InventoryMutationResult
      ] = Left(InventoryError.InventoryNotFound(InventoryId()))
  ): AsyncInventoryService =
    new AsyncInventoryService(null):
      override def create(
          locationId: LocationId,
          skuId: SkuId,
          packagingLevel: PackagingLevel,
          lot: Option[Lot],
          onHand: Int,
          at: Instant
      ): Future[
        Either[InventoryError, InventoryCreateResult]
      ] =
        Future.successful(createResult)

      override def reserve(
          id: InventoryId,
          quantity: Int,
          at: Instant
      ): Future[
        Either[InventoryError, InventoryMutationResult]
      ] =
        Future.successful(reserveResult)

      override def release(
          id: InventoryId,
          quantity: Int,
          at: Instant
      ): Future[
        Either[InventoryError, InventoryMutationResult]
      ] =
        Future.successful(
          Left(
            InventoryError.InventoryNotFound(id)
          )
        )

      override def consume(
          id: InventoryId,
          quantity: Int,
          at: Instant
      ): Future[
        Either[InventoryError, InventoryMutationResult]
      ] =
        Future.successful(
          Left(
            InventoryError.InventoryNotFound(id)
          )
        )

      override def correctLot(
          id: InventoryId,
          newLot: Option[Lot],
          at: Instant
      ): Future[
        Either[InventoryError, InventoryMutationResult]
      ] =
        Future.successful(
          Left(
            InventoryError.InventoryNotFound(id)
          )
        )

  describe("InventoryRoutes"):
    describe("POST /inventory"):
      it("returns 200 with created response on success"):
        val (inventory, event) = Inventory.create(
          locationId,
          skuId,
          PackagingLevel.Each,
          None,
          100,
          at
        )
        val result =
          InventoryCreateResult(inventory, event)
        val routes = InventoryRoutes(
          stubService(Right(result)),
          authService
        )
        val body = s"""{
          |"locationId": "${locationId.value}",
          |"skuId": "${skuId.value}",
          |"packagingLevel": "Each",
          |"onHand": 100
          |}""".stripMargin

        val request = Post(
          "/inventory",
          HttpEntity(ContentTypes.`application/json`, body)
        ).addHeader(Cookie("session", sessionToken))
        request ~> routes ~> check {
          assert(status == StatusCodes.OK)
          val json =
            parse(responseAs[String]).getOrElse(Json.Null)
          assert(
            json.hcursor
              .get[String]("status")
              .contains("created")
          )
        }

    describe("POST /inventory/:id/reserve"):
      it("returns 404 when inventory not found"):
        val routes = InventoryRoutes(
          stubService(
            Left(
              InventoryError.InventoryNotFound(inventoryId)
            ),
            Left(
              InventoryError.InventoryNotFound(inventoryId)
            )
          ),
          authService
        )
        val body = """{"quantity": 10}"""

        val request = Post(
          s"/inventory/${inventoryId.value}/reserve",
          HttpEntity(ContentTypes.`application/json`, body)
        ).addHeader(Cookie("session", sessionToken))
        request ~> routes ~> check {
          assert(status == StatusCodes.NotFound)
        }

      it("returns 409 when insufficient available"):
        val routes = InventoryRoutes(
          stubService(
            Left(
              InventoryError.InventoryNotFound(inventoryId)
            ),
            Left(
              InventoryError.InsufficientAvailable(
                inventoryId,
                50,
                10
              )
            )
          ),
          authService
        )
        val body = """{"quantity": 50}"""

        val request = Post(
          s"/inventory/${inventoryId.value}/reserve",
          HttpEntity(ContentTypes.`application/json`, body)
        ).addHeader(Cookie("session", sessionToken))
        request ~> routes ~> check {
          assert(status == StatusCodes.Conflict)
        }

      it("returns 401 without session cookie"):
        val routes = InventoryRoutes(
          stubService(Right(null)),
          authService
        )
        val body = """{"quantity": 10}"""

        Post(
          s"/inventory/${inventoryId.value}/reserve",
          HttpEntity(ContentTypes.`application/json`, body)
        ) ~> routes ~> check {
          assert(status == StatusCodes.Unauthorized)
        }
