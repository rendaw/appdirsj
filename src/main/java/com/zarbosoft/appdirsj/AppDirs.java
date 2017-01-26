// Copyright (c) 2005-2010 ActiveState Software Inc.
// Copyright (c) 2013 Eddy Petrișor
// Copyright (c) 2017 Rendaw
// Java port by Rendaw.
// Dev Notes:
// - MSDN on where to store app data files:
//   http://support.microsoft.com/default.aspx?scid=kb;en-us;310294#XSLTH3194121123120121120120
// - Mac OS X: http://developer.apple.com/documentation/MacOSX/Conceptual/BPPathSystem/index.html
// - XDG spec for Un*x: http://standards.freedesktop.org/basedir-spec/basedir-spec-latest.html
package com.zarbosoft.appdirsj;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.Shell32;
import com.sun.jna.platform.win32.ShlObj;
import com.sun.jna.platform.win32.WinDef;

import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.zarbosoft.appdirsj.AppDirs.SystemEnum.*;

/**
 * Utilities for determining application-specific dirs.
 * <p>
 * See <a href="http://github.com/rendaw/appdirsj">http://github.com/rendaw/appdirsj</a> for details and usage.
 */
public class AppDirs {
	private FileSystem filesystem = FileSystems.getDefault();

	public static enum SystemEnum {
		WIN32, DARWIN, LINUX2
	}

	private SystemEnum system;
	private String appname;
	private String appauthor;
	private String version;
	private boolean roaming = false;

	public AppDirs() {
		final String os_name = java.lang.System.getProperty("os.name");
		if (os_name.startsWith("Windows")) { // "Windows XP", "Windows 7", etc.
			system = WIN32;
		} else if (os_name.startsWith("Mac")) { // "Mac OS X", etc.
			system = DARWIN;
		} else { // "Linux", "SunOS", "FreeBSD", etc.
			// Setting this to LINUX2 != ideal, but only Windows || Mac
			// are actually checked for && the rest of the module expects
			// *sys.platform* style strings.
			system = LINUX2;
		}
	}

	/**
	 * @param appname is the name of application.
	 *                If None, just the system directory is returned.
	 * @return
	 */
	public AppDirs set_appname(final String appname) {
		this.appname = appname;
		return this;
	}

	/**
	 * @param appauthor is the name of the
	 *                  appauthor or distributing body for this application. Typically
	 *                  it is the owning company name. This falls back to appname. You may
	 *                  pass False to disable it.
	 * @return
	 */
	public AppDirs set_appauthor(final String appauthor) {
		this.appauthor = appauthor;
		return this;
	}

	/**
	 * @param version is an optional version path element to append to the
	 *                path. You might want to use this if you want multiple versions
	 *                of your app to be able to run independently. If used, this
	 *                would typically be "major.minor".
	 *                Only applied when appname is present.
	 * @return
	 */
	public AppDirs set_version(final String version) {
		this.version = version;
		return this;
	}

	/**
	 * @param roaming (boolean, default False) can be set True to use the Windows
	 *                roaming appdata directory. That means that for users on a Windows
	 *                network setup for roaming profiles, this user data will be
	 *                sync'd on login. See
	 *                <a href="http://technet.microsoft.com/en-us/library/cc766489(WS.10).aspx">http://technet.microsoft.com/en-us/library/cc766489(WS.10).aspx</a>
	 *                for a discussion of issues.
	 * @return
	 */
	public AppDirs set_roaming(final boolean roaming) {
		this.roaming = roaming;
		return this;
	}

	/**
	 * @param filesystem
	 * @return
	 */
	public AppDirs set_filesystem(final FileSystem filesystem) {
		this.filesystem = filesystem;
		return this;
	}

	/**
	 * Override the detected filesystem.  Can be used for Unix-style directories on Darwin/Windows.
	 *
	 * @param system
	 * @return
	 */
	public AppDirs set_system(final SystemEnum system) {
		this.system = system;
		return this;
	}

	public SystemEnum get_system() {
		return system;
	}

	/**
	 * Return full path to the user-specific data dir for this application.
	 * <p>
	 * Typical user data directories are:
	 * <table summary="">
	 * <tr><td>Mac OS X</td><td>~/Library/Application Support/{@code appauthor}/{@code appname}</td></tr>
	 * <tr><td>Unix</td><td>~/.local/share/{@code appname}    # or in $XDG_DATA_HOME, if defined</td></tr>
	 * <tr><td>Win XP (not roaming)</td><td>C:\Documents and Settings\{@code username}\Application Data\{@code appauthor}\{@code appname}</td></tr>
	 * <tr><td>Win XP (roaming)</td><td>C:\Documents and Settings\{@code username}\Local Settings\Application Data\{@code appauthor}\{@code appname}</td></tr>
	 * <tr><td>Win 7  (not roaming)</td><td>C:\Users\{@code username}\AppData\Local\{@code appauthor}\{@code appname}</td></tr>
	 * <tr><td>Win 7  (roaming)</td><td>C:\Users\{@code username}\AppData\Roaming\{@code appauthor}\{@code appname}</td></tr>
	 * </table>
	 * <p>
	 * For Unix, we follow the XDG spec and support $XDG_DATA_HOME.
	 * That means, by default "~/.local/share/{@code appname}".
	 *
	 * @return
	 */
	public Path user_data_dir(
	) {
		Path path;
		if (system.equals(WIN32)) {
			path = _get_win_folder(roaming ? ShlObj.CSIDL_APPDATA : ShlObj.CSIDL_LOCAL_APPDATA).normalize();
			if (appname != null) {
				if (appauthor != null) {
					path = filesystem.getPath(path.toString(), appauthor, appname);
				} else {
					path = filesystem.getPath(path.toString(), appname);
				}
			}
		} else if (system.equals(DARWIN)) {
			path = filesystem.getPath(java.lang.System.getProperty("user.home"), "Library/Application Support");
			if (appname != null) {
				if (appauthor != null) {
					path = filesystem.getPath(path.toString(), appauthor, appname);
				} else {
					path = filesystem.getPath(path.toString(), appname);
				}
			}
		} else {
			final String xdgPath = java.lang.System.getenv().get("XDG_DATA_HOME");
			path = xdgPath == null ?
					filesystem.getPath(java.lang.System.getProperty("user.home"), ".local/share") :
					filesystem.getPath(xdgPath);
			if (appname != null) {
				path = filesystem.getPath(path.toString(), appname);
			}
		}
		if (appname != null && version != null) {
			path = filesystem.getPath(path.toString(), version);
		}
		return path;
	}

	/**
	 * Return full path to the user-shared data dir for this application.
	 * <p>
	 * Typical site data directories are:
	 * <table summary="">
	 * <tr><td>Mac OS X</td><td>/Library/Application Support/{@code appauthor}/{@code appname}</td></tr>
	 * <tr><td>Unix</td><td>/usr/local/share/{@code appname} or /usr/share/{@code appname}</td></tr>
	 * <tr><td>Win XP</td><td>C:\Documents and Settings\All Users\Application Data\{@code appauthor}\{@code appname}</td></tr>
	 * <tr><td>Vista</td><td>(Fail! "C:\ProgramData" is a hidden *system* directory on Vista.)</td></tr>
	 * <tr><td>Win 7</td><td>C:\ProgramData\{@code appauthor}\{@code appname}   # Hidden, but writeable on Win 7.</td></tr>
	 * </table>
	 * <p>
	 * For Unix, this is using the $XDG_DATA_DIRS[0] default.
	 * <p>
	 * WARNING: Do not use this on Windows. See the Vista-Fail note above for why.
	 *
	 * @return
	 */
	public List<Path> site_data_dir() {
		Path path;
		if (system.equals(WIN32)) {
			if (appauthor == null) {
				appauthor = appname;
			}
			path = _get_win_folder(ShlObj.CSIDL_COMMON_APPDATA).normalize();
			if (appname != null) {
				if (appauthor != null) {
					path = filesystem.getPath(path.toString(), appauthor, appname);
				} else {
					path = filesystem.getPath(path.toString(), appname);
				}
			}
		} else if (system.equals(DARWIN)) {
			path = filesystem.getPath("/Library/Application Support");
			if (appname != null) {
				if (appauthor != null) {
					path = filesystem.getPath(path.toString(), appauthor, appname);
				} else {
					path = filesystem.getPath(path.toString(), appname);
				}
			}
		} else {
			// XDG default for $XDG_DATA_DIRS
			// only first, if multipath == false
			final String xdgPath = java.lang.System.getenv().get("XDG_DATA_DIRS");
			final List<Path> pathlist = xdgPath == null ?
					Arrays.asList(filesystem.getPath("/usr/local/share"), filesystem.getPath("/usr/share")) :
					Arrays.stream(xdgPath.split(filesystem.getSeparator())).map(new Function<String, Path>() {
						public Path apply(final String s) {
							return filesystem.getPath(s);
						}
					}).collect(Collectors.<Path>toList());
			if (appname != null) {
				if (version != null) {
					appname = String.format("%s/%s", appname, version);
				}
				final String finalAppname = appname;
				pathlist.addAll(pathlist.stream().map(new Function<Path, Path>() {
					public Path apply(final Path file) {
						return filesystem.getPath(file.toString(), finalAppname);
					}
				}).collect(Collectors.<Path>toList()));
			}
			return pathlist;
		}
		if (appname != null && version != null) {
			path = filesystem.getPath(path.toString(), version);
		}
		return Arrays.asList(path);
	}

	/**
	 * Return full path to the user-specific config dir for this application.
	 * <p>
	 * Typical user config directories are:
	 * <table summary="">
	 * <tr><td>Mac OS X</td><td>~/Library/Preferences/{@code appname}</td></tr>
	 * <tr><td>Unix</td><td>~/.config/{@code appname}     # or in $XDG_CONFIG_HOME, if defined</td></tr>
	 * <tr><td>Win *</td><td>same as user_data_dir</td></tr>
	 * </table>
	 * <p>
	 * For Unix, we follow the XDG spec and support $XDG_CONFIG_HOME.
	 * That means, by default "~/.config/{@code appname}".
	 *
	 * @return
	 */
	public Path user_config_dir() {
		Path path;
		if (system == WIN32) {
			path = user_data_dir();
		} else if (system == DARWIN) {
			path = filesystem.getPath(System.getProperty("user.home"), "Library/Preferences");
			if (appname != null)
				path = filesystem.getPath(path.toString(), appname);
		} else {
			final String xdgPath = java.lang.System.getenv().get("XDG_CONFIG_HOME");
			path = xdgPath == null ?
					filesystem.getPath(java.lang.System.getProperty("user.home"), ".config") :
					filesystem.getPath(xdgPath);
			if (appname != null) {
				path = filesystem.getPath(path.toString(), appname);
			}
		}
		if (appname != null && version != null) {
			path = filesystem.getPath(path.toString(), version);
		}
		return path;
	}

	/**
	 * Return full path to the user-shared data dir for this application.
	 * <p>
	 * Typical site config directories are:
	 * <table summary="">
	 * <tr><td>Mac OS X</td><td>/Library/Preferences/{@code appauthor}/{@code appname}</td></tr>
	 * <tr><td>Unix</td><td>/etc/xdg/{@code appname} or $XDG_CONFIG_DIRS[i]/{@code appname} for each value in $XDG_CONFIG_DIRS</td></tr>
	 * <tr><td>Win *</td><td>same as site_data_dir</td></tr>
	 * <tr><td>Vista</td><td>(Fail! "C:\ProgramData" is a hidden *system* directory on Vista.)</td></tr>
	 * </table>
	 * <p>
	 * For Unix, this is using the $XDG_CONFIG_DIRS[0] default, if multipath=False
	 * <p>
	 * WARNING: Do not use this on Windows. See the Vista-Fail note above for why.
	 *
	 * @return
	 */
	public List<Path> site_config_dir(String appname, final String appauthor, final String version) {
		if (system == WIN32) {
			Path path;
			path = site_data_dir().get(0);
			if (appname != null && version != null) {
				path = filesystem.getPath(path.toString(), version);
			}
			return Arrays.asList(path);
		} else if (system == DARWIN) {
			Path path;
			path = filesystem.getPath("/Library/Preferences");
			if (appname != null)
				path = filesystem.getPath(path.toString(), appname);
			return Arrays.asList(path);
		} else {
			// XDG default for $XDG_CONFIG_DIRS
			// only first, if multipath == false
			final String xdgPath = java.lang.System.getenv().get("XDG_CONFIG_DIRS");
			final List<Path> pathlist = xdgPath == null ?
					Arrays.asList(filesystem.getPath("/etc/xdg")) :
					Arrays.stream(xdgPath.split(filesystem.getSeparator())).map(new Function<String, Path>() {
						public Path apply(final String s) {
							return filesystem.getPath(s);
						}
					}).collect(Collectors.<Path>toList());
			if (appname != null) {
				if (version != null) {
					appname = String.format("%s/%s", appname, version);
				}
				final String finalAppname = appname;
				pathlist.addAll(pathlist.stream().map(new Function<Path, Path>() {
					public Path apply(final Path file) {
						return filesystem.getPath(file.toString(), finalAppname);
					}
				}).collect(Collectors.<Path>toList()));
			}
			return pathlist;
		}
	}

	/**
	 * Return full path to the user-specific cache dir for this application.
	 * <p>
	 * Typical user cache directories are:
	 * <table summary="">
	 * <tr><td>Mac OS X</td><td>~/Library/Caches/{@code appauthor}/{@code appname}</td></tr>
	 * <tr><td>Unix</td><td>~/.cache/{@code appname} (XDG default)</td></tr>
	 * <tr><td>Win XP</td><td>C:\Documents and Settings\{@code username}\Local Settings\Application Data\{@code appauthor}\{@code appname}\Cache</td></tr>
	 * <tr><td>Vista</td><td>C:\Users\{@code username}\AppData\Local\{@code appauthor}\{@code appname}\Cache</td></tr>
	 * </table>
	 * <p>
	 * On Windows the only suggestion in the MSDN docs is that local settings go in
	 * the `CSIDL_LOCAL_APPDATA` directory. This is identical to the non-roaming
	 * app data dir (the default returned by `user_data_dir` above). Apps typically
	 * put cache data somewhere *under* the given dir here. Some examples:
	 * ...\Mozilla\Firefox\Profiles\*ProfileName*\Cache
	 * ...\Acme\SuperApp\Cache\1.0
	 * OPINION: This function appends "Cache" to the `CSIDL_LOCAL_APPDATA` value.
	 * This can be disabled with the `opinion=False` option.
	 *
	 * @return
	 */
	public Path user_cache_dir(final boolean opinion) {
		Path path;
		if (system.equals(WIN32)) {
			if (appauthor == null) {
				appauthor = appname;
			}
			path = _get_win_folder(ShlObj.CSIDL_LOCAL_APPDATA).normalize();
			if (appname != null) {
				if (appauthor != null) {
					path = filesystem.getPath(path.toString(), appauthor, appname);
				} else {
					path = filesystem.getPath(path.toString(), appname);
				}
				if (opinion) {
					path = filesystem.getPath(path.toString(), "Cache");
				}
			}
		} else if (system.equals(DARWIN)) {
			path = filesystem.getPath(java.lang.System.getProperty("user.home"), "Library/Caches");
			if (appname != null) {
				if (appauthor != null) {
					path = filesystem.getPath(path.toString(), appauthor, appname);
				} else {
					path = filesystem.getPath(path.toString(), appname);
				}
			}
		} else {
			final String xdgPath = java.lang.System.getenv().get("XDG_CACHE_HOME");
			path = xdgPath == null ?
					filesystem.getPath(java.lang.System.getProperty("user.home"), ".cache") :
					filesystem.getPath(xdgPath);
			if (appname != null) {
				path = filesystem.getPath(path.toString(), appname);
			}
		}
		if (appname != null && version != null) {
			path = filesystem.getPath(path.toString(), version);
		}
		return path;
	}

	/**
	 * Return full path to the user-specific state dir for this application.
	 * <p>
	 * Typical user state directories are:
	 * <table summary="">
	 * <tr><td>Mac OS X</td><td>same as user_data_dir</td></tr>
	 * <tr><td>Unix</td><td>~/.local/state/{@code appname}   # or in $XDG_STATE_HOME, if defined</td></tr>
	 * <tr><td>Win *</td><td>same as user_data_dir</td></tr>
	 * </table>
	 * <p>
	 * For Unix, we follow this Debian proposal <a href="https://wiki.debian.org/XDGBaseDirectorySpecification#state">https://wiki.debian.org/XDGBaseDirectorySpecification#state</a>
	 * to extend the XDG spec and support $XDG_STATE_HOME.
	 * <p>
	 * That means, by default "~/.local/state/{@code appname}".
	 *
	 * @return
	 */
	public Path user_state_dir(
	) {
		Path path;
		if (system == WIN32 || system == DARWIN) {
			path = user_data_dir();
		} else {
			final Path fallback = filesystem.getPath(java.lang.System.getProperty("user.home"), ".local/state");
			final String xdgPath = java.lang.System.getenv().get("XDG_STATE_HOME");
			path = xdgPath == null ? fallback : filesystem.getPath(xdgPath);
			if (appname != null) {
				path = filesystem.getPath(path.toString(), appname);
			}
		}
		if (appname != null && version != null) {
			path = filesystem.getPath(path.toString(), version);
		}
		return path;
	}

	/**
	 * Return full path to the user-specific log dir for this application.
	 * <p>
	 * Typical user log directories are:
	 * <table summary="">
	 * <tr><td>Mac OS X</td><td>~/Library/Logs/{@code appauthor}/{@code appname}</td></tr>
	 * <tr><td>Unix</td><td>~/.cache/{@code appname}/log  # or under $XDG_CACHE_HOME if defined</td></tr>
	 * <tr><td>Win XP</td><td>C:\Documents and Settings\{@code username}\Local Settings\Application Data\{@code appauthor}\{@code appname}\Logs</td></tr>
	 * <tr><td>Vista</td><td>C:\Users\{@code username}\AppData\Local\{@code appauthor}\{@code appname}\Logs</td></tr>
	 * </table>
	 * <p>
	 * On Windows the only suggestion in the MSDN docs is that local settings
	 * go in the `CSIDL_LOCAL_APPDATA` directory. (Note: I'm interested in
	 * examples of what some windows apps use for a logs dir.)
	 * <p>
	 * OPINION: This function appends "Logs" to the `CSIDL_LOCAL_APPDATA`
	 * value for Windows and appends "log" to the user cache dir for Unix.
	 * This can be disabled with the `opinion=False` option.
	 *
	 * @return
	 */
	public Path user_log_dir(final boolean opinion) {
		Path path;
		if (system.equals(DARWIN)) {
			path = filesystem.getPath(java.lang.System.getProperty("user.home"), "Library/Logs");
			if (appname != null) {
				if (appauthor != null) {
					path = filesystem.getPath(path.toString(), appauthor, appname);
				} else {
					path = filesystem.getPath(path.toString(), appname);
				}
			}
		} else if (system.equals(WIN32)) {
			path = user_data_dir();
			version = null;
			if (opinion) {
				path = filesystem.getPath(path.toString(), "Logs");
			}
		} else {
			path = user_cache_dir(true);
			version = null;
			if (opinion) {
				path = filesystem.getPath(path.toString(), "log");
			}
		}
		if (appname != null && version != null) {
			path = filesystem.getPath(path.toString(), version);
		}
		return path;
	}

	private Path _get_win_folder(final int csidl) {
		final int buf_size = WinDef.MAX_PATH * 2;
		String dir;
		{
			final char[] buf = new char[buf_size];
			final Shell32 shell = Shell32.INSTANCE;
			shell.SHGetFolderPath(null, csidl, null, ShlObj.SHGFP_TYPE_CURRENT, buf);
			dir = Native.toString(buf).trim();
		}
		// Downgrade to short path name if have highbit chars. See
		// <a href="http://bugs.activestate.com/show_bug.cgi?id=85099">http://bugs.activestate.com/show_bug.cgi?id=85099</a>.
		boolean has_high_char = false;
		for (int i = 0; i < dir.length(); ++i) {
			final int c = dir.codePointAt(i);
			if (c > 255) {
				has_high_char = true;
				break;
			}
		}
		if (has_high_char) {
			final char[] buf = new char[buf_size];
			final Kernel32 kernel = Kernel32.INSTANCE;
			if (kernel.GetShortPathName(dir, buf, buf_size) != 0) {
				dir = Native.toString(buf).trim();
			}
		}
		return filesystem.getPath(dir);
	}
}

