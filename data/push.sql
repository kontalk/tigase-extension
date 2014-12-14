-- phpMyAdmin SQL Dump
-- version 4.2.12deb1
-- http://www.phpmyadmin.net
--
-- Host: localhost
-- Generation Time: Dec 14, 2014 at 02:11 PM
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
-- Table structure for table `push`
--

CREATE TABLE `push` (
  `user_id` varchar(300) CHARACTER SET utf8 NOT NULL COMMENT 'User ID',
  `provider` varchar(20) NOT NULL COMMENT 'Push provider',
  `reg_id` varchar(1000) NOT NULL COMMENT 'Registration ID'
) ENGINE=MyISAM DEFAULT CHARSET=ascii COMMENT='Verification codes';

--
-- Indexes for dumped tables
--

--
-- Indexes for table `push`
--
ALTER TABLE `push`
 ADD PRIMARY KEY (`user_id`,`provider`);

/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
