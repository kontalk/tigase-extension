-- phpMyAdmin SQL Dump
-- version 3.3.7deb8
-- http://www.phpmyadmin.net
--
-- Host: localhost
-- Generation Time: Mar 04, 2015 at 10:41 AM
-- Server version: 5.5.38
-- PHP Version: 5.3.29-1~dotdeb.0

SET SQL_MODE="NO_AUTO_VALUE_ON_ZERO";


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8 */;

--
-- Database: `tigase`
--

-- --------------------------------------------------------

--
-- Table structure for table `servers`
--

CREATE TABLE `servers` (
  `fingerprint` char(40) NOT NULL COMMENT 'Server key fingerprint',
  `host` varchar(100) NOT NULL COMMENT 'Server address',
  `enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT 'Server enabled in the network',
  PRIMARY KEY (`fingerprint`)
) ENGINE=MyISAM DEFAULT CHARSET=ascii COMMENT='Servers';
