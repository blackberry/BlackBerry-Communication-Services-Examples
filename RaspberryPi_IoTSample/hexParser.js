//
//  Copyright (c) 2018 BlackBerry.  All Rights Reserved.
//
//  Licensed under the Apache License, Version 2.0 (the "License");
//  you may not use this file except in compliance with the License.
//  You may obtain a copy of the License at
// 
//  http://www.apache.org/licenses/LICENSE-2.0
// 
//  Unless required by applicable law or agreed to in writing, software
//  distributed under the License is distributed on an "AS IS" BASIS,
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  See the License for the specific language governing permissions and
//  limitations under the License.
//
 
const Gpio = require('pigpio').Gpio;

//  Assign 3 GPIO's to the RGB LED.
var ledR = new Gpio(17, {mpde: Gpio.OUTPUT}),
	ledG = new Gpio(27, {mpde: Gpio.OUTPUT}),
	ledB = new Gpio(22, {mpde: Gpio.OUTPUT});

//  PWM write green to the RGB LED upon init.
ledR.pwmWrite(0);
ledG.pwmWrite(255);
ledB.pwmWrite(0);

module.exports = function(hexString) {
	//  Hex string is of form #FFFFFF. 
	//  Parse the passed hexidecimal string into red, green, and blue and convert to
	//  base 10 integers. Values will be 8-bit (range from 0 to 255).
	const redHex = parseInt(hexString.substr(1, 2), 16) || 0;
	const greHex = parseInt(hexString.substr(3, 2), 16) || 0;
	const bluHex = parseInt(hexString.substr(5, 2), 16) || 0;
	
	//  PWM write the 8-bit values to the RGB LED.
	ledR.pwmWrite(redHex);
	ledG.pwmWrite(greHex);
	ledB.pwmWrite(bluHex);
};
