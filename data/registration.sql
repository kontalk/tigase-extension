-- phpMyAdmin SQL Dump
-- version 4.2.12deb1
-- http://www.phpmyadmin.net
--
-- Host: localhost
-- Generation Time: Dec 14, 2014 at 02:12 PM
-- Server version: 5.5.40-1
-- PHP Version: 5.6.2-1

SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";
SET time_zone = "+00:00";


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8 */;

--
-- Database: `tigase`
--

-- --------------------------------------------------------

--
-- Table structure for table `validations`
--

CREATE TABLE `validations` (
  `user_id` varchar(300) CHARACTER SET utf8 NOT NULL COMMENT 'User ID',
  `code` char(6) NOT NULL COMMENT 'Verification code',
  `timestamp` datetime DEFAULT NULL COMMENT 'Validation code timestamp'
) ENGINE=MyISAM DEFAULT CHARSET=ascii COMMENT='Verification codes';

--
-- Indexes for dumped tables
--

--
-- Indexes for table `validations`
--
ALTER TABLE `validations`
 ADD PRIMARY KEY (`user_id`), ADD UNIQUE KEY `code` (`code`);

/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
