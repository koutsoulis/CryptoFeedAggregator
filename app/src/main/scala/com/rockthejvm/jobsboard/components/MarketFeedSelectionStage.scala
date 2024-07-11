package com.rockthejvm.jobsboard.components

import com.rockthejvm.jobsboard.App.Model
import com.rockthejvm.jobsboard.App.Msg
import tyrian.*
import tyrian.Html.*
import names.ExchangeName
import marketData.names.Currency
import monocle.syntax.all.*
import marketData.names.FeedName.OrderbookFeed
import marketData.names.FeedName
import com.rockthejvm.jobsboard.components.MarketFeedSelectionStage.SelectFeed.LegalFeedNameString
import scala.util.Try
import marketData.names.TradePair
import marketData.names.FeedName.FeedNameQ

sealed trait MarketFeedSelectionStage {
  def view: Html[Msg] = div(selectsBackingView)
  def selectsBackingView: List[Html[Msg]]
}

object MarketFeedSelectionStage {

  case class SelectExchange(
      tradePairs: Map[names.ExchangeName, Map[Currency, Set[Currency]]],
      enabled: Boolean = true
  ) extends MarketFeedSelectionStage {
    override def selectsBackingView: List[Html[Msg]] = List {
      val options: List[Html[Msg]] =
        tradePairs
          .keySet.map(_.toString).toList.prepended("No Exchange selected")
          .map(tyrian.Html.option[Msg](_))

      select(
        Option.when(!enabled)(Attribute("disabled", "")).toList `appended`
          onInput { value =>
            Try(ExchangeName.valueOf(value))
              .toOption.fold(
                ifEmpty = this
              ) { selectedExchange =>
                SelectFeed(
                  previousStep = this.focus(_.enabled).replace(false),
                  exchangeSelected = selectedExchange,
                  tradePairs = tradePairs.get(selectedExchange).get
                )
              }

          }
      )(options)
    }
  }

  case class SelectFeed(
      previousStep: SelectExchange,
      exchangeSelected: ExchangeName,
      tradePairs: Map[Currency, Set[Currency]],
      enabled: Boolean = true
  ) extends MarketFeedSelectionStage {
    override def selectsBackingView: List[Html[Msg]] = previousStep.selectsBackingView.appended {
      val options: List[Html[Msg]] =
        SelectFeed
          .feedNames.map(_.toString).prepended("No market feed selected")
          .map(tyrian.Html.option[Msg](_))

      select(
        Option.when(!enabled)(Attribute("disabled", "")).toList `appended`
          onInput {
            case "Orderbook" =>
              SelectCurrency1(
                previousStep = this.focus(_.enabled).replace(false),
                exchangeSelected = exchangeSelected,
                feedNameAsPartialResult = { currency1 => currency2 => FeedName.OrderbookFeed(TradePair(currency1, currency2)) },
                tradePairs = tradePairs
              )
            case "Candlesticks" =>
              SelectCurrency1(
                previousStep = this.focus(_.enabled).replace(false),
                exchangeSelected = exchangeSelected,
                feedNameAsPartialResult = { currency1 => currency2 => FeedName.Candlesticks(TradePair(currency1, currency2)) },
                tradePairs = tradePairs
              )
            case "Stub" => this
            case _ => this
          }
      )(options)
    }
  }

  object SelectFeed {
    type LegalFeedNameString = "Orderbook" | "Candlesticks"
    val feedNames: List[LegalFeedNameString] = List("Orderbook", "Candlesticks")
  }

  case class SelectCurrency1(
      previousStep: SelectFeed,
      exchangeSelected: ExchangeName,
      feedNameAsPartialResult: Currency => Currency => FeedNameQ,
      tradePairs: Map[Currency, Set[Currency]],
      enabled: Boolean = true
  ) extends MarketFeedSelectionStage {
    override def selectsBackingView: List[Html[Msg]] = previousStep.selectsBackingView.appended {
      val options: List[Html[Msg]] =
        tradePairs
          .keySet.toList.map(_.name).sorted.prepended("No currency selected")
          .map(tyrian.Html.option[Msg](_))

      select(
        Option.when(!enabled)(Attribute("disabled", "")).toList `appended`
          onInput { currencyName =>
            if (currencyName == "No currency selected") {
              this
            } else {
              val currency1 = Currency(currencyName)
              SelectCurrency2(
                previousStep = this.focus(_.enabled).replace(false),
                exchangeSelected = exchangeSelected,
                feedNameAsPartialResult = feedNameAsPartialResult(currency1),
                secondComponentForFixedFirst = tradePairs(currency1)
              )
            }
          }
      )(options)
    }
  }

  case class SelectCurrency2(
      previousStep: SelectCurrency1,
      exchangeSelected: ExchangeName,
      feedNameAsPartialResult: Currency => FeedNameQ,
      secondComponentForFixedFirst: Set[Currency],
      enabled: Boolean = true
  ) extends MarketFeedSelectionStage {
    override def selectsBackingView: List[Html[Msg]] = previousStep.selectsBackingView.appended {
      val options: List[Html[Msg]] =
        secondComponentForFixedFirst
          .toList.map(_.name).sorted.prepended("No currency selected")
          .map(tyrian.Html.option[Msg](_))

      select(
        Option.when(!enabled)(Attribute("disabled", "")).toList `appended`
          onInput { currencyName =>
            if (currencyName == "No currency selected") {
              this
            } else {
              val currency2 = Currency(currencyName)
              TotalSelection(
                previousStep = this.focus(_.enabled).replace(false),
                exchangeSelected = exchangeSelected,
                feedName = feedNameAsPartialResult(currency2)
              )
            }
          }
      )(options)
    }
  }

  case class TotalSelection(
      previousStep: MarketFeedSelectionStage,
      exchangeSelected: ExchangeName,
      feedName: FeedNameQ
  ) extends MarketFeedSelectionStage {
    override def selectsBackingView: List[Html[Msg]] = previousStep.selectsBackingView
  }
}
