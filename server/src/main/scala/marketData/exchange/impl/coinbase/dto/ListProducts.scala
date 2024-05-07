package marketData.exchange.impl.coinbase.dto

import _root_.io.circe
import _root_.io.circe.generic.semiauto.*

// https://docs.cloud.coinbase.com/advanced-trade/reference/retailbrokerageapi_getpublicproducts

final case class ListProducts(
    products: List[ListProducts.Product]
) derives circe.Decoder

object ListProducts {
  case class Product(
      base_currency_id: String,
      quote_currency_id: String,
      is_disabled: Boolean
  ) derives circe.Decoder
}
