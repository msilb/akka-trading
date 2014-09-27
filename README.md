akka-trading
============

Backtesting + Live Trading Framework built on top of Akka/Spray

What is akka-trading good for?
==============================

This framework can be useful for people coming from Scala background who are making their first steps in implementing backtesting and automated trading strategies using Oanda's REST API, which is in my opinion one of the best available APIs for retail clients. Since this is work in progress, if you are a Scala enthusiast and are interested in automated trading, have a look and feel free to contribute!

Usage
=====

Just check out the code, modify StrategyFSM to include your trading logic. Don't forget to modify AuthInfo trait to include your own account ID and access token for Oanda's REST API.
