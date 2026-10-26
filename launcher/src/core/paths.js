'use strict';
const path = require('path');

/** Wszystkie katalogi launchera w jednym miejscu. */
function createPaths(root) {
	return {
		root,
		libraries: path.join(root, 'libraries'),
		assets: path.join(root, 'assets'),
		versions: path.join(root, 'versions'),
		runtime: path.join(root, 'runtime'),
		instances: path.join(root, 'instances'),
		cache: path.join(root, 'cache'),
		state: path.join(root, 'launcher.json'),
	};
}

module.exports = { createPaths };
