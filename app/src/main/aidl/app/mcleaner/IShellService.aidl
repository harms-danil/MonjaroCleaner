package app.mcleaner;

interface IShellService {
    /** Reserved by Shizuku for destroying the user service. */
    void destroy() = 16777114;

    /** Runs `sh -c command`; returns "<exitCode>\n<stdout+stderr>". */
    String exec(String command) = 1;

    /** Deletes the cache of one package for one user. False when the platform refused. */
    boolean deleteCache(String packageName, int userId) = 2;

    /** Removes the given packages' tasks from Recents (the system «running apps» screen). Returns how many. */
    int removeTasks(in String[] packageNames, int userId) = 3;
}
