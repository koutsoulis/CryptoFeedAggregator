package com.rockthejvm.jobsboard.modules

import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.secretsmanager.SecretsManagerAsyncClient
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse

import cats.effect.*
import cats.*
import cats.data.*
import cats.syntax.all.*
import _root_.io.circe
import _root_.io.circe.generic.semiauto.*

trait SecretsRepo {
  def cryptocompareApiKey: String
}

object SecretsRepo {
  def apply[F[_]: Async]: F[SecretsRepo] = {
    // we bundle all secrets as Key-Value pairs under this name on AWS Secrets Manager
    val secretName = "secrets"
    val region = Region.EU_NORTH_1

    val client = SecretsManagerAsyncClient
      .builder()
      .region(region)
      .build()

    val getSecretValueRequest = GetSecretValueRequest
      .builder()
      .secretId(secretName)
      .build();

    Async[F]
      .fromCompletableFuture(
        Async[F].delay(client.getSecretValue(getSecretValueRequest))
      ).map { getSecretValueResponse =>
        circe.parser.decode[SecretsKV](getSecretValueResponse.secretString())
      }.rethrow
      .flatTap(asd => Async[F].delay(println(asd)))
      .map { secretsKV =>
        new SecretsRepo {
          override val cryptocompareApiKey: String = secretsKV.cryptocompare
        }
      }
  }

  case class SecretsKV(
      cryptocompare: String
  ) derives circe.Decoder
}
