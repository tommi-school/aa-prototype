DROP DATABASE IF EXISTS `exchange`;
CREATE DATABASE `exchange`
USE `exchange`;

DROP TABLE IF EXISTS `ask`;
CREATE TABLE `ask` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `stock` varchar(3) DEFAULT NULL,
  `price` int(11) DEFAULT NULL,
  `user` varchar(20) DEFAULT NULL,
  `date` datetime DEFAULT NULL,
  `has_transacted` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=32 DEFAULT CHARSET=utf8;

DROP TABLE IF EXISTS `bid`;
CREATE TABLE `bid` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `stock` varchar(3) DEFAULT NULL,
  `price` int(11) DEFAULT NULL,
  `user` varchar(20) DEFAULT NULL,
  `date` datetime DEFAULT NULL,
  `has_transacted` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=82 DEFAULT CHARSET=utf8;

DROP TABLE IF EXISTS `transaction`;
CREATE TABLE `transaction` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `bid_id` int(11) DEFAULT NULL,
  `ask_id` int(11) DEFAULT NULL,
  `date` datetime DEFAULT NULL,
  `price` int(11) DEFAULT NULL,
  `stock` varchar(3) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=20 DEFAULT CHARSET=utf8;