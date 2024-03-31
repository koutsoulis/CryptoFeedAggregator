package com.rockthejvm.jobsboard.pages

import tyrian.Html
import tyrian.*
import tyrian.Html.*
import tyrian.CSS.*
import com.rockthejvm.jobsboard.App.Msg
import com.rockthejvm.jobsboard.App.Model
import _root_.io.circe.syntax.*
import tyrian.Attr

class HomePage extends Page {
  override def view(model: Model): Html[Msg] = {
    println(s"view called with sells head: ${model.sells.head}")
    def stupidhack(_int: Int) = script("""
    var jsonDataElement = document.getElementById('jsonData');
    var dataTextContent = jsonDataElement.textContent;

    // Parse the JSON string into a JavaScript array
    var dataArray;
    try {
      dataArray = JSON.parse(dataTextContent);
    } catch (error) {
      console.error("Error parsing JSON data", error);
    }

    var cdata1 = {
      "background-color": "#f3f3f3 #d9d9d9",
      "type": "depth",
      "options": {
        "currency": "$"
      },
      "series": [{
          "values": dataArray,
          "text": "Sell"
        },
        {
          "values": [
            [1168.49, 0],
            [1172.22, 33.1932],
            [1174.28, 50.5177],
            [1174.99, 81.8346],
            [1189.53, 104.332],
            [1191.07, 119.9178],
            [1195.62, 146.3812],
            [1199.32, 180.9109],
            [1201.89, 199.313],
            [1204.34, 228.9945],
            [1206.47, 251.6454],
            [1209.44, 285.6366],
            [1221.89, 312.7949],
            [1230.48, 328.6889],
            [1235.24, 351.3438],
            [1248.33, 377.9289],
            [1251.24, 409.9444],
            [1253.75, 435.5418],
            [1257.48, 453.8852],
            [1261.01, 483.8769],
            [1265.06, 499.7163],
            [1268.75, 529.6374],
            [1270.2, 552.1779],
            [1272.15, 579.5218],
            [1274.19, 606.4376],
            [1276.17, 638.8508],
            [1283.07, 668.7969],
            [1285.76, 694.1647],
            [1287.89, 709.9417],
            [1288.72, 735.6358],
            [1295.71, 765.2281],
            [1303.26, 784.6807],
            [1305.43, 801.1021],
            [1307.78, 817.4528],
            [1312.76, 836.7914],
            [1317.6, 859.4746],
            [1322.31, 891.443],
            [1324.35, 907.6098],
            [1325.7, 931.1996],
            [1528.01, 949.3013]
          ],
          "text": "Buy"
        }
      ]
    };

    zingchart.render({
      id: 'zc',
      data: cdata1
    });
    """)
    def zc2 = div(
      id := "zc"
    )("asd asd")
    val maxVolume = model.sells.map(_._2).max

    def outerRow(elems: List[tyrian.Elem[Msg]]): tyrian.Html[Msg] = div(
      style(
        CSS.height("30px") |+|
          CSS.width("100%") |+|
          CSS.position("relative")
      )
    )(elems)

    def row(elems: List[tyrian.Elem[Msg]]): tyrian.Html[Msg] = div(
      style(
        CSS.display("flex") |+|
          CSS.position("absolute") |+|
          CSS.width("100%") |+|
          CSS.top("0")
      )
    )(elems)

    def cell(text: String): tyrian.Html[Msg] = div(
      style(
        CSS.`flex-grow`("1") |+|
          CSS.display("flex") |+|
          CSS.`justify-content`("center") |+|
          CSS.`align-items`("center") |+|
          CSS.color("black")
      )
    )(text)

    def percentageBar(width: Double): tyrian.Html[Msg] = div(
      style(
        CSS.`background-color`("green") |+|
          CSS.height("100%") |+|
          CSS.position("absolute") |+|
          CSS.right("0") |+|
          CSS.width(s"$width%")
      )
    )("")

    val rows: List[tyrian.Html[Msg]] = model.sells.map { case (price, volume) =>
      outerRow(
        List(
          percentageBar(volume * 100 / maxVolume),
          row(
            List(
              cell(price.toString),
              cell(volume.toString)
            )
          )
        )
      )
    }
    div(style(CSS.display("flex")))(
      div(style(CSS.flex("1")))(
        children = rows: List[tyrian.Html[Msg]]
      ),
      div(style(CSS.flex("1")))(
        children = rows: List[tyrian.Html[Msg]]
      )
    )
  }

}
