
import com.google.gson.Gson;
import java.io.File;
import static java.nio.file.StandardWatchEventKinds.*;

import java.io.IOException;
import java.nio.Buffer;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jgroups.JChannel;
import org.jgroups.Message;
import org.jgroups.ReceiverAdapter;

public class Client extends ReceiverAdapter {

    private final WatchService watcher;
    private final Map<WatchKey, Path> keys;
    private static Arquivos arquivosCliente;
    private static String nomeDosArquivos;
    private JChannel channel;
    private static int tam;
    private static Path dir;
    private File file;

    /**
     * Creates a WatchService and registers the given directory
     */
    Client(Path dir) throws IOException, Exception {
        this.watcher = FileSystems.getDefault().newWatchService();
        this.keys = new HashMap<WatchKey, Path>();
        this.arquivosCliente = new Arquivos();
        walkAndRegisterDirectories(dir);
    }

    /**
     * Register the given directory, and all its sub-directories, with the
     * WatchService.
     */
    private void walkAndRegisterDirectories(final Path start) throws IOException {
        Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                registerDirectory(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Register the given directory with the WatchService; This function will be
     * called by FileVisitor
     */
    private void registerDirectory(Path dir) throws IOException {
        WatchKey key = dir.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
        keys.put(key, dir);
    }

    /**
     * Process all events for keys queued to the watcher
     */
    void processEvents() throws Exception {
        for (;;) {

            // wait for key to be signalled
            WatchKey key;
            try {
                key = watcher.take();
            } catch (InterruptedException x) {
                return;
            }

            Path dir = keys.get(key);
            if (dir == null) {
                System.err.println("WatchKey not recognized!!");
                continue;
            }

            for (WatchEvent<?> event : key.pollEvents()) {
                @SuppressWarnings("rawtypes")
                WatchEvent.Kind kind = event.kind();

                // Context for directory entry event is the file name of entry
                @SuppressWarnings("unchecked")
                Path name = ((WatchEvent<Path>) event).context();
                Path child = dir.resolve(name);

                // print out event
                System.out.format("%s: %s\n", event.kind().name(), child);

                // if directory is created, and watching recursively, then register it and its sub-directories
                if (kind == ENTRY_CREATE) {
                    try {
                        if (Files.isDirectory(child)) {
                            walkAndRegisterDirectories(child);
                            arquivosCliente.getModificados().add(new TratamentoArquivos(child.toString(), 2, tam));
                        } else {
                            arquivosCliente.getModificados().add(new TratamentoArquivos(child.toString(), 1, tam));
                        }
                    } catch (IOException x) {
                        // do something useful
                    }

                    raise();
                } else if (kind == ENTRY_DELETE) {

                    String[] auxPath = child.toString().split("/");
                    nomeDosArquivos = auxPath[auxPath.length - 1];

                    Path paths = Paths.get(child.toString());
                    Path pai = paths.getParent();

                    arquivosCliente.getDeletados().add((pai.toString().substring(tam) + "/" + nomeDosArquivos));

                    raise();
                }
            }

            // reset key and remove from set if directory no longer accessible
            boolean valid = key.reset();
            if (!valid) {
                keys.remove(key);

                // all directories are inaccessible
                if (keys.isEmpty()) {
                    break;
                }
            }
        }
    }

    public void raise() {
        try {
            Message msg = new Message(null, arquivosCliente); //A null destination address sends the message to everyone in the cluster
            this.channel.send(msg);
            arquivosCliente = new Arquivos();
        } catch (Exception e) {
        }
    }

    public void receive(Message msg) {
//       Arquivos arquivosRecebidos = new Arquivos();
//       arquivosRecebidos = msg.getObject();
//        System.out.println("Nome Cliente::"+arquivosRecebidos.getNomeCliente());
        ArrayList<TratamentoArquivos> arquivosRecebidosServidor = msg.getObject();
        gravar(arquivosRecebidosServidor);
        for (int i = 0; i < arquivosRecebidosServidor.size(); i++) {
            System.out.println("" + arquivosRecebidosServidor.get(i).getNomeArquivo());
        }

    }

    public void conectarClienteAoServidor() throws Exception {
        this.channel = new JChannel();
        this.channel.setDiscardOwnMessages(true);
        this.channel.connect("Luziane");
        this.channel.setReceiver(this);
    }

    public void gravar(ArrayList<TratamentoArquivos> copiaArquivos) {
        do {
            for (int i = 0; i < copiaArquivos.size(); i++) {
                file = new File((dir + copiaArquivos.get(i).getNomeDirPai() + copiaArquivos.get(i).getNomeArquivo()));
                if (!file.exists()) {
                    if (copiaArquivos.get(i).getTipo() == 1) {
                        file = new File((dir + copiaArquivos.get(i).getNomeDirPai()));
                        if (file.exists()) {
                            copiaArquivos.get(i).setArquivoNoDisk((dir + copiaArquivos.get(i).getNomeDirPai()));
                            copiaArquivos.remove(i);
                        }
                    } else {
                        file = new File((dir + copiaArquivos.get(i).getNomeDirPai()));
                        if (file.exists()) {
                            copiaArquivos.get(i).setDiretorios((dir + copiaArquivos.get(i).getNomeDirPai()));
                            copiaArquivos.remove(i);
                        }
                    }
                }
            }
        } while (!copiaArquivos.isEmpty());

    }

    public void verifica(String nome) {
        try (Stream<Path> walk = Files.walk(Paths.get(nome))) {

            List<String> diretorios = walk.filter(Files::isDirectory).map(x -> x.toString()).collect(Collectors.toList());

            for (int i = 0; i < diretorios.size(); i++) {
                arquivosCliente.getInicial().add(new TratamentoArquivos(diretorios.get(i), 2, tam));
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        try (Stream<Path> walk = Files.walk(Paths.get(nome))) {

            List<String> arquivos = walk.filter(Files::isRegularFile).map(x -> x.toString()).collect(Collectors.toList());

            for (int i = 0; i < arquivos.size(); i++) {
                arquivosCliente.getInicial().add(new TratamentoArquivos(arquivos.get(i), 1, tam));
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws IOException, Exception {
        boolean logout = true;
        String caminho = "";
        System.out.println("Bem vindo ao Dropbox");
        System.out.println("->Digite 1 para conectar");
        System.out.println("->Digite 0 para sair");
        while (logout) {
            
            
            
            Scanner leitura = new Scanner(System.in);
            int op = leitura.nextInt();

            if (op == 1) {
                System.out.println("Digite o caminho da sua pasta:");
                Scanner ler = new Scanner(System.in);
                caminho = ler.nextLine();
                //"/home/luziane/Documentos/cliente"
                dir = Paths.get(caminho);
                Path pai = dir.getParent();
                tam = pai.toAbsolutePath().toString().length();
                String nomeCliente = dir.toString().substring(tam);

                Client cliente = new Client(dir);
                arquivosCliente.setNomeCliente(nomeCliente);
                cliente.conectarClienteAoServidor();

                cliente.verifica(dir.toString());

                cliente.raise();

                Thread minhaThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            cliente.processEvents();
                        } catch (Exception ex) {
                            Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }
                });

                minhaThread.start();
            } else if (op == 0) {
                logout = false;
                Runtime.getRuntime().exec("rm -R "+caminho);
                System.exit(0);
            }
            System.out.println("->Digite 0 para sair");

        }

    }
}
