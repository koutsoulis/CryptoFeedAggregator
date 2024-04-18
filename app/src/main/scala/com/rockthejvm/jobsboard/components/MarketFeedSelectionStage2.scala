package com.rockthejvm.jobsboard.components

import com.rockthejvm.jobsboard.App.Model
import com.rockthejvm.jobsboard.App.Msg
import tyrian.*
import tyrian.Html.*
import names.Exchange
import marketData.Currency
import monocle.syntax.all.*
import marketData.FeedDefinition.OrderbookFeed
import marketData.FeedDefinition
import com.rockthejvm.jobsboard.components.MarketFeedSelectionStage2.SelectFeed.FeedName
import scala.util.Try

sealed trait MarketFeedSelectionStage2 {
  def view: Html[Msg] = div(selectsBackingView)
  def selectsBackingView: List[Html[Msg]]
}

object MarketFeedSelectionStage2 {

  case class SelectExchange(
      tradePairs: Map[names.Exchange, Map[Currency, Set[Currency]]],
      enabled: Boolean = true
  ) extends MarketFeedSelectionStage2 {
    override def selectsBackingView: List[Html[Msg]] = List {
      val options: List[Html[Msg]] =
        tradePairs
          .keySet.map(_.toString).toList.prepended("No Exchange selected")
          .map(tyrian.Html.option[Msg](_))

      select(
        Option.when(!enabled)(Attribute("disabled", "")).toList `appended`
          onInput { value =>
            Try(Exchange.valueOf(value))
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
      exchangeSelected: Exchange,
      tradePairs: Map[Currency, Set[Currency]],
      enabled: Boolean = true
  ) extends MarketFeedSelectionStage2 {
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
                feedNameAsPartialResult = FeedDefinition.OrderbookFeed.apply.curried,
                tradePairs = tradePairs
              )
            case "Stub" => this
            case _ => this
          }
      )(options)
    }
  }

  object SelectFeed {
    type FeedName = "Orderbook" | "Stub"
    val feedNames: List[FeedName] = List("Orderbook", "Stub")
  }

  case class SelectCurrency1(
      previousStep: SelectFeed,
      exchangeSelected: Exchange,
      feedNameAsPartialResult: Currency => Currency => FeedDefinition[?],
      tradePairs: Map[Currency, Set[Currency]],
      enabled: Boolean = true
  ) extends MarketFeedSelectionStage2 {
    override def selectsBackingView: List[Html[Msg]] = previousStep.selectsBackingView.appended {
      val options: List[Html[Msg]] =
        tradePairs
          .keySet.toList.map(_.name).prepended("No currency selected")
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
      exchangeSelected: Exchange,
      feedNameAsPartialResult: Currency => FeedDefinition[?],
      secondComponentForFixedFirst: Set[Currency],
      enabled: Boolean = true
  ) extends MarketFeedSelectionStage2 {
    override def selectsBackingView: List[Html[Msg]] = previousStep.selectsBackingView.appended {
      val options: List[Html[Msg]] =
        secondComponentForFixedFirst
          .toList.map(_.name).prepended("No currency selected")
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
      previousStep: MarketFeedSelectionStage2,
      exchangeSelected: Exchange,
      feedName: FeedDefinition[?]
  ) extends MarketFeedSelectionStage2 {
    override def selectsBackingView: List[Html[Msg]] = previousStep.selectsBackingView
  }
}
